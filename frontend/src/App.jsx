import React, { useState, useEffect, useRef } from 'react'
import axios from 'axios'
import './App.css'
import Tabs from './components/Tabs'
import Monitoring from './components/Monitoring'
import RequirementsPanel from './components/RequirementsPanel'

function App() {

  const [updates, setUpdates] = useState([])
  const processedLinksRef = useRef(new Set())
  const [feeds, setFeeds] = useState([])
  const [monitoringItems, setMonitoringItems] = useState([])
  const [allowedMap, setAllowedMap] = useState({})
  const [existingPolicy, setExistingPolicy] = useState('')
  const [newRegulation, setNewRegulation] = useState('')
  const [response, setResponse] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [parsed, setParsed] = useState(null)
  const [activeTab, setActiveTab] = useState('monitoring')
  const [localRequirements, setLocalRequirements] = useState([])
  const [tickets, setTickets] = useState([])
  const [notification, setNotification] = useState(null)

  useEffect(() => {
    if (activeTab === 'requirements') {
      axios.get('http://localhost:8080/api/policy/tickets')
        .then(res => setTickets(res.data || []))
        .catch(() => {})
    }
    if (activeTab === 'workflow') {
      loadUpdates()
    }
  }, [activeTab])

  const analyze = async () => {
    if (!existingPolicy.trim() || !newRegulation.trim()) {
      setError('Both policy and regulation fields are required')
      return
    }
    setLoading(true)
    setError('')
    setResponse('')
    try {
      const res = await axios.post(
        'http://localhost:8080/api/policy/analyze',
        { existingPolicy, newRegulation },
        { timeout: 300000 }
      )
      setResponse(typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2))
      const obj = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
      setParsed(obj)
      setLocalRequirements(obj.requirements || [])
      setActiveTab('impact')
    } catch (err) {
      if (err.code === 'ECONNABORTED') {
        setError('Request timeout. Please try again.')
      } else if (!err.response) {
        setError('Cannot connect to backend. Ensure the Spring Boot app is running.')
      } else {
        setError(err.response?.data?.message || err.message)
      }
    } finally {
      setLoading(false)
    }
  }

  const handleNewRegulation = (item) => {
    const link = item.link
    if (!item.analysis) return
    if (processedLinksRef.current.has(link)) return
    processedLinksRef.current.add(link)
    const analysis = item.analysis
    setNotification('New regulatory update detected and analyzed')
    setTimeout(() => setNotification(null), 4000)
    setParsed(analysis)
    setLocalRequirements(analysis.requirements || [])
  }

  const handleRequirementsUpdate = (updated) => {
    setLocalRequirements(updated)
  }

  const generateTicket = (req) => {
    if (req && req.id && req.createdAt) {
      setTickets(prev => [req, ...prev])
      return
    }
    const payload = {
      requirementId: req.id,
      summary: req.text,
      recommendation: req.recommendation
    }
    axios.post('http://localhost:8080/api/policy/tickets', payload)
      .then(res => setTickets(prev => [res.data, ...prev]))
      .catch(err => {
        console.error('Ticket creation failed', err)
        alert('Ticket creation failed')
      })
  }

  const exportPDF = async () => {
    if (!parsed) return
    const res = await fetch('http://localhost:8080/api/policy/export/pdf', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(parsed)
    })
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'policy-drafts.pdf'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }

  const exportDOCX = async () => {
    if (!parsed) return
    const res = await fetch('http://localhost:8080/api/policy/export/docx', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(parsed)
    })
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'policy-drafts.docx'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }

  const loadUpdates = async () => {
    const res = await fetch('http://localhost:8080/api/policy/updates')
    const json = await res.json()
    setUpdates(json || [])
    const map = {}
    for (const u of json) {
      const a = await fetch(`http://localhost:8080/api/policy/${u.id}/allowed-transitions`)
      map[u.id] = await a.json()
    }
    setAllowedMap(map)
  }

  const transitionUpdate = async (id, action) => {
    await fetch(`http://localhost:8080/api/policy/${id}/${action}`, { method: 'POST' })
    loadUpdates()
  }

  const statusActionMap = {
    REVIEW_PENDING: 'submit-review',
    APPROVED: 'approve',
    IMPLEMENTED: 'mark-implemented',
    CLOSED: 'close'
  }

  const statusColors = {
    NEW: '#6b7280',
    ANALYZED: '#3b82f6',
    REVIEW_PENDING: '#f59e0b',
    APPROVED: '#10b981',
    IMPLEMENTED: '#8b5cf6',
    CLOSED: '#374151'
  }

  const summary = parsed?.summary

  return (
    <div className="container">

      {loading && (
        <div className="overlay">
          <div className="loaderBox">
            <div className="spinner" />
            <span>Analyzing with AI...</span>
          </div>
        </div>
      )}

      <header>
        <div className="header-icon">⚖️</div>
        <h1>Policy-as-Code Regulatory Change Agent</h1>
        <p className="subtitle">AI-powered regulatory analysis, gap detection & policy drafting</p>
      </header>

      {notification && (
        <div className="notification">
          🔔 {notification}
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

        {/* ========== MONITORING ========== */}
        {activeTab === 'monitoring' &&
          <div className="card full-width">
            <Monitoring
              feeds={feeds}
              setFeeds={setFeeds}
              items={monitoringItems}
              setItems={setMonitoringItems}
              onNewRegulation={handleNewRegulation}
              loading={loading}
              setLoading={setLoading}
            />
          </div>
        }

        {/* ========== IMPACT ========== */}
        {activeTab === 'impact' &&
          <div className="card full-width">
            <h3 className="section-title">📊 Impact Summary</h3>
            {!summary ? (
              <div className="empty-state">
                <span>🔍</span>
                <p>Run an analysis to view the impact summary</p>
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

                {parsed?.confidenceScore != null && (
                  <div className="confidence-bar-wrap">
                    <div className="confidence-label">
                      Confidence Score: <strong>{Math.round(parsed.confidenceScore * 100)}%</strong>
                    </div>
                    <div className="confidence-track">
                      <div
                        className="confidence-fill"
                        style={{ width: `${Math.round(parsed.confidenceScore * 100)}%` }}
                      />
                    </div>
                  </div>
                )}

                {/* Business Line & System Impact from requirements */}
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
              requirements={localRequirements.length ? localRequirements : (parsed?.requirements || [])}
              onUpdate={handleRequirementsUpdate}
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
                      <span className={`ticket-status status-${(t.status || 'OPEN').toLowerCase()}`}>
                        {t.status || 'OPEN'}
                      </span>
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
              <div className="empty-state">
                <span>🔍</span>
                <p>Run an analysis to view the gap report</p>
              </div>
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
              {parsed?.policyDrafts?.length > 0 && (
                <div className="export-btns">
                  <button className="btn" onClick={exportPDF}>Export PDF</button>
                  <button className="btn" onClick={exportDOCX}>Export DOCX</button>
                </div>
              )}
            </div>
            {!parsed?.policyDrafts?.length ? (
              <div className="empty-state">
                <span>✍️</span>
                <p>No policy drafts generated yet. Drafts are created for requirements with recommendation: <code>update_policy</code></p>
              </div>
            ) : (
              <div className="drafts-list">
                {parsed.policyDrafts.map((d, i) => (
                  <div key={i} className="draft-card">
                    <div className="draft-header">
                      <span className="draft-req-id">Requirement #{d.requirementId}</span>
                      <button
                        className="btn copy-btn"
                        onClick={() => navigator.clipboard.writeText(d.draft).then(() => alert('Copied!'))}
                      >
                        Copy
                      </button>
                    </div>
                    <div className="draft-body">{d.draft}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        }

        {/* ========== CODE GEN ========== */}
        {activeTab === 'code' &&
          <div className="card full-width">
            <h3 className="section-title">💻 Code Specifications</h3>
            {!parsed?.codeSpecifications?.length ? (
              <div className="empty-state">
                <span>💻</span>
                <p>No code specifications generated yet. Specs are created for requirements with recommendation: <code>implement_system_rule</code></p>
              </div>
            ) : (
              <div className="code-list">
                {parsed.codeSpecifications.map((c, i) => (
                  <div key={i} className="code-card">
                    <div className="code-header">
                      <span className="code-req-id">Requirement #{c.requirementId}</span>
                      <button
                        className="btn copy-btn"
                        onClick={() => navigator.clipboard.writeText(c.spec).then(() => alert('Copied!'))}
                      >
                        Copy
                      </button>
                    </div>
                    <pre className="code-block">{c.spec}</pre>
                  </div>
                ))}
              </div>
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
              <div className="empty-state">
                <span>🔄</span>
                <p>No regulatory updates found. Run an analysis first.</p>
              </div>
            ) : (
              <div className="workflow-list">
                {updates.map(u => (
                  <div key={u.id} className="workflow-card">
                    <div className="workflow-header">
                      <div className="workflow-meta">
                        <span className="workflow-id">#{u.id}</span>
                        <span className="workflow-title">{u.title || 'Regulatory Update'}</span>
                        <span className="workflow-authority">{u.authority}</span>
                      </div>
                      <span
                        className="workflow-status"
                        style={{ background: statusColors[u.status] || '#6b7280' }}
                      >
                        {u.status}
                      </span>
                    </div>
                    <div className="workflow-date">
                      {u.publicationDate ? new Date(u.publicationDate).toLocaleDateString() : ''}
                    </div>
                    <div className="workflow-actions">
                      {(allowedMap[u.id] || []).map(a => (
                        <button
                          key={a}
                          className="btn primary"
                          onClick={() => transitionUpdate(u.id, statusActionMap[a])}
                        >
                          → {a.replace('_', ' ')}
                        </button>
                      ))}
                      {(allowedMap[u.id] || []).length === 0 && (
                        <span className="workflow-final">No further transitions available</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        }

        {/* ========== INPUT SECTION (always visible) ========== */}
        <div className="input-section">
          <h3 className="section-title">🧾 Run Analysis</h3>

          <div className="input-group">
            <label>Existing Policy</label>
            <textarea
              rows="6"
              placeholder={`Example:\npolicy:\n  name: VendorRiskPolicy\n  reviewFrequencyDays: 180`}
              value={existingPolicy}
              onChange={e => setExistingPolicy(e.target.value)}
            />
          </div>

          <div className="input-group">
            <label>New Regulation</label>
            <textarea
              rows="6"
              placeholder={`Example:\nAll critical vendors must undergo a risk assessment every 180 days.`}
              value={newRegulation}
              onChange={e => setNewRegulation(e.target.value)}
            />
          </div>

          <button
            className="analyze-btn"
            disabled={loading}
            onClick={analyze}
          >
            {loading ? '⏳ Analyzing...' : '🚀 Analyze'}
          </button>

          {error && <div className="error">⚠️ {error}</div>}
        </div>

      </div>
    </div>
  )
}

export default App
