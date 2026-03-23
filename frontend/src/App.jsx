import React, { useState, useEffect, useRef } from 'react'
import axios from 'axios'
import './App.css'
import Tabs from './components/Tabs'
import Monitoring from './components/Monitoring'
import RequirementsPanel from './components/RequirementsPanel'

function App() {

  const [updates, setUpdates] = useState([])
  const [updateSummaries, setUpdateSummaries] = useState({})
  const processedLinksRef = useRef(new Set())
  const [feeds, setFeeds] = useState([])
  const [monitoringItems, setMonitoringItems] = useState([])
  const [allowedMap, setAllowedMap] = useState({})
  const [existingPolicy, setExistingPolicy] = useState('')
  const [newRegulation, setNewRegulation] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [activeTab, setActiveTab] = useState('monitoring')
  const [tickets, setTickets] = useState([])
  const [notification, setNotification] = useState(null)

  // ── Analysis History ──────────────────────────────────────────────
  // Each entry: { id, label, source, timestamp, data }
  const historyCounterRef = useRef(0)
  const [analysisHistory, setAnalysisHistory] = useState([])
  const [selectedAnalysisId, setSelectedAnalysisId] = useState(null)

  // Derived: the currently selected analysis object
  const selectedAnalysis = analysisHistory.find(a => a.id === selectedAnalysisId) || null
  const parsed = selectedAnalysis?.data || null
  const localRequirements = parsed?.requirements || []

  // Session metrics across ALL analyses
  const [sessionMetrics, setSessionMetrics] = useState({
    regulationsDetected: 0,
    requirementsExtracted: 0,
    gapsFound: 0,
    draftsGenerated: 0,
    codeSpecsGenerated: 0
  })

  useEffect(() => {
    if (activeTab === 'requirements') {
      axios.get('http://localhost:8080/api/policy/tickets')
        .then(res => setTickets(res.data || []))
        .catch(() => {})
    }
    if (activeTab === 'workflow') loadUpdates()
  }, [activeTab])

  // ── Add a new analysis to history and select it ───────────────────
  const addToHistory = (data, label, source = 'Manual') => {
    historyCounterRef.current += 1
    const id = historyCounterRef.current
    const entry = {
      id,
      label: label || `Analysis #${analysisHistory.length + 1}`,
      source,
      timestamp: new Date().toLocaleString(),
      data
    }
    setAnalysisHistory(prev => [entry, ...prev])
    setSelectedAnalysisId(id)
    setSessionMetrics(prev => ({
      regulationsDetected: prev.regulationsDetected + 1,
      requirementsExtracted: prev.requirementsExtracted + (data?.requirements?.length || 0),
      gapsFound: prev.gapsFound + (data?.gapReport?.length || 0),
      draftsGenerated: prev.draftsGenerated + (data?.policyDrafts?.length || 0),
      codeSpecsGenerated: prev.codeSpecsGenerated + (data?.codeSpecifications?.length || 0)
    }))
  }

  // ── Manual analysis ───────────────────────────────────────────────
  const analyze = async () => {
    if (!existingPolicy.trim() || !newRegulation.trim()) {
      setError('Both policy and regulation fields are required')
      return
    }
    setLoading(true)
    setError('')
    try {
      const res = await axios.post('http://localhost:8080/api/policy/analyze',
        { existingPolicy, newRegulation }, { timeout: 300000 })
      const obj = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
      // Use first line of regulation as label
      const label = newRegulation.trim().split('\n')[0].substring(0, 60)
      addToHistory(obj, label, 'Manual')
      setActiveTab('impact')
    } catch (err) {
      if (err.code === 'ECONNABORTED') setError('Request timeout. Please try again.')
      else if (!err.response) setError('Cannot connect to backend.')
      else setError(err.response?.data?.message || err.message)
    } finally {
      setLoading(false)
    }
  }

  // ── RSS feed analysis ─────────────────────────────────────────────
  const handleNewRegulation = (item) => {
    if (!item.analysis) return
    if (processedLinksRef.current.has(item.link)) return
    processedLinksRef.current.add(item.link)
    const label = (item.title || 'Feed Item').substring(0, 60)
    addToHistory(item.analysis, label, 'RSS Feed')
    setNotification(`🔔 New regulation analyzed: "${label}..."`)
    setTimeout(() => setNotification(null), 6000)
  }

  // ── Ticket generation ─────────────────────────────────────────────
  const generateTicket = (req) => {
    if (req && req.id && req.createdAt) { setTickets(prev => [req, ...prev]); return }
    axios.post('http://localhost:8080/api/policy/tickets', {
      requirementId: req.dbId || req.id,
      summary: req.text,
      recommendation: req.recommendation
    }).then(res => setTickets(prev => [res.data, ...prev]))
      .catch(() => alert('Ticket creation failed'))
  }

  const exportPDF = async () => {
    if (!parsed) return
    const res = await fetch('http://localhost:8080/api/policy/export/pdf', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(parsed)
    })
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = 'policy-drafts.pdf'
    document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url)
  }

  const exportDOCX = async () => {
    if (!parsed) return
    const res = await fetch('http://localhost:8080/api/policy/export/docx', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(parsed)
    })
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = 'policy-drafts.docx'
    document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url)
  }

  const loadUpdates = async () => {
    const res = await fetch('http://localhost:8080/api/policy/updates')
    const json = await res.json()
    setUpdates(json || [])
    const map = {}, summaries = {}
    for (const u of json) {
      const [at, st] = await Promise.all([
        fetch(`http://localhost:8080/api/policy/${u.id}/allowed-transitions`).then(r => r.json()),
        fetch(`http://localhost:8080/api/policy/updates/${u.id}/summary`).then(r => r.json())
      ])
      map[u.id] = at
      summaries[u.id] = st
    }
    setAllowedMap(map)
    setUpdateSummaries(summaries)
  }

  const transitionUpdate = async (id, action) => {
    await fetch(`http://localhost:8080/api/policy/${id}/${action}`, { method: 'POST' })
    loadUpdates()
  }

  const statusActionMap = {
    REVIEW_PENDING: 'submit-review', APPROVED: 'approve',
    IMPLEMENTED: 'mark-implemented', CLOSED: 'close'
  }
  const statusColors = {
    NEW: '#6b7280', ANALYZED: '#3b82f6', REVIEW_PENDING: '#f59e0b',
    APPROVED: '#10b981', IMPLEMENTED: '#8b5cf6', CLOSED: '#374151'
  }

  const summary = parsed?.summary

  // ── Analysis History Selector ─────────────────────────────────────
  const HistorySelector = () => {
    if (analysisHistory.length === 0) return null
    return (
      <div className="history-bar">
        <span className="history-label">📂 Viewing:</span>
        <div className="history-tabs">
          {analysisHistory.map(a => (
            <button
              key={a.id}
              className={`history-tab ${selectedAnalysisId === a.id ? 'active' : ''}`}
              onClick={() => setSelectedAnalysisId(a.id)}
              title={`${a.source} — ${a.timestamp}`}
            >
              <span className={`history-source-dot ${a.source === 'Manual' ? 'manual' : 'feed'}`} />
              <span className="history-tab-label">{a.label}</span>
              <span className="history-tab-time">{a.timestamp}</span>
            </button>
          ))}
        </div>
        <div className="history-legend">
          <span className="legend-dot manual" /> Manual
          <span className="legend-dot feed" style={{ marginLeft: 10 }} /> RSS Feed
        </div>
      </div>
    )
  }

  return (
    <div className="container">

      {loading && (
        <div className="overlay">
          <div className="loaderBox">
            <div className="spinner" />
            <span>AI Agent analyzing regulation...</span>
          </div>
        </div>
      )}

      <header>
        <div className="header-icon">⚖️</div>
        <h1>Policy-as-Code Regulatory Change Agent</h1>
        <p className="subtitle">Regulatory Monitoring · Impact Assessment · Requirement Extraction · Gap Analysis · Policy Drafting · Code Generation</p>
        <div className="agent-tag">🤖 Groq LLM · Spring Boot · React · Real-time RSS Monitoring</div>
      </header>

      {/* SESSION METRICS BANNER */}
      {sessionMetrics.regulationsDetected > 0 && (
        <div className="metrics-banner">
          <div className="metric-item">
            <span className="metric-num">{sessionMetrics.regulationsDetected}</span>
            <span className="metric-label">Regulations Detected</span>
          </div>
          <div className="metric-divider" />
          <div className="metric-item">
            <span className="metric-num">{sessionMetrics.requirementsExtracted}</span>
            <span className="metric-label">Requirements Extracted</span>
          </div>
          <div className="metric-divider" />
          <div className="metric-item">
            <span className="metric-num">{sessionMetrics.gapsFound}</span>
            <span className="metric-label">Gaps Identified</span>
          </div>
          <div className="metric-divider" />
          <div className="metric-item">
            <span className="metric-num">{sessionMetrics.draftsGenerated}</span>
            <span className="metric-label">Policy Drafts</span>
          </div>
          <div className="metric-divider" />
          <div className="metric-item">
            <span className="metric-num">{sessionMetrics.codeSpecsGenerated}</span>
            <span className="metric-label">Code Specs</span>
          </div>
        </div>
      )}

      {notification && (
        <div className="notification">
          {notification}
          <button className="notif-close" onClick={() => setNotification(null)}>✕</button>
        </div>
      )}

      <div className="content">

        <div style={{ gridColumn: '1 / -1', marginBottom: 12 }}>
          <Tabs
            tabs={[
              { key: 'monitoring', title: '📡 Monitoring' },
              { key: 'impact', title: '📊 Impact' },
              { key: 'requirements', title: '📋 Requirements' },
              { key: 'gaps', title: '🔍 Gap Analysis' },
              { key: 'drafts', title: '✍️ Drafting' },
              { key: 'code', title: '💻 Code Gen' },
              { key: 'workflow', title: '🔄 Workflow' }
            ]}
            active={activeTab}
            onChange={setActiveTab}
          />
        </div>

        {/* HISTORY SELECTOR — shown on all content tabs except monitoring and workflow */}
        {['impact','requirements','gaps','drafts','code'].includes(activeTab) && (
          <div style={{ gridColumn: '1 / -1' }}>
            <HistorySelector />
          </div>
        )}

        {/* ========== MONITORING ========== */}
        {activeTab === 'monitoring' &&
          <div className="card full-width">
            <Monitoring feeds={feeds} setFeeds={setFeeds} items={monitoringItems}
              setItems={setMonitoringItems} onNewRegulation={handleNewRegulation}
              loading={loading} setLoading={setLoading} />
          </div>
        }

        {/* ========== IMPACT ========== */}
        {activeTab === 'impact' &&
          <div className="card full-width">
            <div className="section-header">
              <h3 className="section-title">📊 Impact Summary</h3>
              {parsed?.agentName && <span className="agent-provenance">🤖 {parsed.agentName}</span>}
            </div>
            {!summary ? (
              <div className="empty-state">
                <span>🔍</span>
                <p>Run an analysis or fetch a regulatory feed to view the impact summary.</p>
              </div>
            ) : (
              <>
                <div className="summary-grid">
                  <div className="summary-card total">
                    <div className="summary-number">{summary.totalRequirements || 0}</div>
                    <div className="summary-label">Total Requirements</div>
                  </div>
                  <div className="summary-card satisfied">
                    <div className="summary-number">{summary.alreadySatisfied || 0}</div>
                    <div className="summary-label">Already Satisfied</div>
                  </div>
                  <div className="summary-card policy">
                    <div className="summary-number">{summary.policyUpdatesNeeded || 0}</div>
                    <div className="summary-label">Policy Updates Needed</div>
                  </div>
                  <div className="summary-card controls">
                    <div className="summary-number">{summary.newControlsNeeded || 0}</div>
                    <div className="summary-label">New Controls Needed</div>
                  </div>
                  <div className="summary-card system">
                    <div className="summary-number">{summary.systemImplementationsNeeded || 0}</div>
                    <div className="summary-label">System Implementations</div>
                  </div>
                </div>

                <div className="routing-summary">
                  <div className="routing-card compliance">
                    <div className="routing-icon">👔</div>
                    <div className="routing-info">
                      <div className="routing-team">Compliance Team</div>
                      <div className="routing-count">
                        {(summary.policyUpdatesNeeded || 0) + (summary.newControlsNeeded || 0)} items to review
                      </div>
                      <div className="routing-desc">Policy amendments + new controls</div>
                    </div>
                  </div>
                  <div className="routing-arrow">→</div>
                  <div className="routing-card technology">
                    <div className="routing-icon">💻</div>
                    <div className="routing-info">
                      <div className="routing-team">Technology Team</div>
                      <div className="routing-count">
                        {summary.systemImplementationsNeeded || 0} items to implement
                      </div>
                      <div className="routing-desc">System rules + code specifications</div>
                    </div>
                  </div>
                </div>

                {parsed?.confidenceScore != null && (
                  <div className="confidence-bar-wrap">
                    <div className="confidence-label">
                      AI Confidence Score: <strong>{Math.round(parsed.confidenceScore * 100)}%</strong>
                    </div>
                    <div className="confidence-track">
                      <div className="confidence-fill"
                        style={{ width: `${Math.round(parsed.confidenceScore * 100)}%` }} />
                    </div>
                  </div>
                )}

                {localRequirements.length > 0 && (
                  <div className="impact-breakdown">
                    <h4>Business Line Impact</h4>
                    <div className="impact-tags">
                      {[...new Set(localRequirements.map(r => r.impactedBusinessLine).filter(Boolean))].map(bl => (
                        <span key={bl} className="impact-tag business">{bl}</span>
                      ))}
                    </div>
                    <h4 style={{ marginTop: 16 }}>System Impact</h4>
                    <div className="impact-tags">
                      {[...new Set(localRequirements.map(r => r.impactedSystem).filter(Boolean))].map(sys => (
                        <span key={sys} className="impact-tag system">{sys}</span>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        }

        {/* ========== REQUIREMENTS ========== */}
        {activeTab === 'requirements' &&
          <div style={{ gridColumn: '1 / -1' }}>
            <RequirementsPanel
              requirements={localRequirements}
              onUpdate={() => {}}
              onGenerateTicket={generateTicket}
            />
            {tickets.length > 0 && (
              <div className="card" style={{ marginTop: 16 }}>
                <h4 className="section-title">🎫 Generated Tickets</h4>
                <div className="tickets-list">
                  {tickets.map((t, i) => (
                    <div key={i} className="ticket-item">
                      <div className="ticket-key">{t.trackingKey || `TICKET-${t.id}`}</div>
                      <div className="ticket-summary">{t.summary}</div>
                      <div className="ticket-right">
                        {t.assignedTeam && (
                          <span className={`team-badge team-${(t.assignedTeam || '').toLowerCase()}`}>
                            {t.assignedTeam === 'TECHNOLOGY' ? '💻' : t.assignedTeam === 'COMPLIANCE' ? '👔' : '🛡️'} {t.assignedTeam}
                          </span>
                        )}
                        <span className={`ticket-status status-${(t.status || 'OPEN').toLowerCase()}`}>
                          {t.status || 'OPEN'}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        }

        {/* ========== GAP ANALYSIS ========== */}
        {activeTab === 'gaps' &&
          <div className="card full-width">
            <h3 className="section-title">🔍 Gap Analysis</h3>
            {!parsed?.gapReport?.length ? (
              <div className="empty-state"><span>🔍</span><p>Select an analysis above or run a new one.</p></div>
            ) : (
              <div className="gaps-list">
                {parsed.gapReport.map((g, i) => (
                  <div key={i} className="gap-card">
                    <div className="gap-header">
                      <span className="gap-req-id">Requirement #{g.requirementId}</span>
                      <span className="gap-badge">Gap Identified</span>
                    </div>
                    <div className="gap-issue">⚠️ {g.issue}</div>
                    <div className="gap-detail">{g.detail}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        }

        {/* ========== DRAFTING ========== */}
        {activeTab === 'drafts' &&
          <div className="card full-width">
            <div className="section-header">
              <h3 className="section-title">✍️ Policy Drafts</h3>
              {/* {parsed?.policyDrafts?.length > 0 && (
                <div className="export-btns">
                  <button className="btn" onClick={exportPDF}>Export PDF</button>
                  <button className="btn" onClick={exportDOCX}>Export DOCX</button>
                </div>
              )} */}
            </div>
            {!parsed?.policyDrafts?.length ? (
              <div className="empty-state"><span>✍️</span>
                <p>No drafts for this analysis. Drafts are generated for <code>update_policy</code> and <code>add_control</code> requirements.</p>
              </div>
            ) : (
              <>
                <div className="draft-routing-note">
                  👔 Routed to <strong>Compliance Team</strong> for legal review and approval.
                </div>
                <div className="drafts-list">
                  {parsed.policyDrafts.map((d, i) => (
                    <div key={i} className="draft-card">
                      <div className="draft-header">
                        <span className="draft-req-id">Requirement #{d.requirementId}</span>
                        <button className="btn copy-btn"
                          onClick={() => navigator.clipboard.writeText(d.draft).then(() => alert('Copied!'))}>
                          Copy
                        </button>
                      </div>
                      <div className="draft-body">{d.draft}</div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        }

        {/* ========== CODE GEN ========== */}
        {activeTab === 'code' &&
          <div className="card full-width">
            <h3 className="section-title">💻 Code Specifications</h3>
            {!parsed?.codeSpecifications?.length ? (
              <div className="empty-state"><span>💻</span>
                <p>No code specs for this analysis. Generated for <code>implement_system_rule</code> requirements.</p>
              </div>
            ) : (
              <>
                <div className="draft-routing-note technology">
                  💻 Routed to <strong>Technology Team</strong> for system implementation.
                </div>
                <div className="code-list">
                  {parsed.codeSpecifications.map((c, i) => (
                    <div key={i} className="code-card">
                      <div className="code-header">
                        <span className="code-req-id">Requirement #{c.requirementId}</span>
                        <button className="btn copy-btn"
                          onClick={() => navigator.clipboard.writeText(c.spec).then(() => alert('Copied!'))}>
                          Copy
                        </button>
                      </div>
                      <pre className="code-block">{c.spec}</pre>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        }

        {/* ========== WORKFLOW ========== */}
        {activeTab === 'workflow' &&
          <div className="card full-width">
            <div className="section-header">
              <h3 className="section-title">🔄 Regulatory Workflow</h3>
              <button className="btn" onClick={loadUpdates}>↻ Refresh</button>
            </div>
            {updates.length === 0 ? (
              <div className="empty-state"><span>🔄</span><p>No regulatory updates found. Run an analysis first.</p></div>
            ) : (
              <div className="workflow-list">
                {updates.map(u => {
                  const s = updateSummaries[u.id]
                  return (
                    <div key={u.id} className="workflow-card">
                      <div className="workflow-header">
                        <div className="workflow-meta">
                          <span className="workflow-id">#{u.id}</span>
                          <span className="workflow-title">{u.title || 'Regulatory Update'}</span>
                          <span className="workflow-authority">{u.authority}</span>
                        </div>
                        <span className="workflow-status"
                          style={{ background: statusColors[u.status] || '#6b7280' }}>
                          {u.status}
                        </span>
                      </div>
                      <div className="workflow-date">
                        {u.publicationDate ? new Date(u.publicationDate).toLocaleDateString() : ''}
                        {u.sourceLink && (
                          <a href={u.sourceLink} target="_blank" rel="noreferrer"
                            className="workflow-source-link"> ↗ Source</a>
                        )}
                      </div>
                      {s && (
                        <div className="workflow-breakdown">
                          <div className="wf-stat"><span className="wf-num">{s.totalRequirements}</span><span className="wf-lbl">Requirements</span></div>
                          <div className="wf-stat satisfied"><span className="wf-num">{s.alreadySatisfied}</span><span className="wf-lbl">Satisfied</span></div>
                          <div className="wf-stat policy"><span className="wf-num">{s.policyUpdatesNeeded}</span><span className="wf-lbl">Policy Updates</span></div>
                          <div className="wf-stat controls"><span className="wf-num">{s.newControlsNeeded}</span><span className="wf-lbl">New Controls</span></div>
                          <div className="wf-stat system"><span className="wf-num">{s.systemImplementationsNeeded}</span><span className="wf-lbl">System Rules</span></div>
                        </div>
                      )}
                      {s && (
                        <div className="workflow-routing">
                          <div className="routing-pill compliance">👔 Compliance: {s.routedToCompliance} items</div>
                          <div className="routing-pill technology">💻 Technology: {s.routedToTechnology} items</div>
                        </div>
                      )}
                      <div className="workflow-actions">
                        {(allowedMap[u.id] || []).map(a => (
                          <button key={a} className="btn primary"
                            onClick={() => transitionUpdate(u.id, statusActionMap[a])}>
                            → {a.replace('_', ' ')}
                          </button>
                        ))}
                        {(allowedMap[u.id] || []).length === 0 && (
                          <span className="workflow-final">✅ No further transitions available</span>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        }

        {/* ========== INPUT SECTION ========== */}
        <div className="input-section">
          <h3 className="section-title">🧾 Run Analysis</h3>
          <div className="input-group">
            <label>Existing Policy</label>
            <textarea rows="6"
              placeholder={`Example:\npolicy:\n  name: VendorRiskPolicy\n  reviewFrequencyDays: 180`}
              value={existingPolicy} onChange={e => setExistingPolicy(e.target.value)} />
          </div>
          <div className="input-group">
            <label>New Regulation</label>
            <textarea rows="6"
              placeholder={`Example:\nAll critical vendors must undergo a risk assessment every 180 days.`}
              value={newRegulation} onChange={e => setNewRegulation(e.target.value)} />
          </div>
          <button className="analyze-btn" disabled={loading} onClick={analyze}>
            {loading ? '⏳ Analyzing...' : '🚀 Analyze'}
          </button>
          {error && <div className="error">⚠️ {error}</div>}
        </div>

      </div>
    </div>
  )
}

export default App