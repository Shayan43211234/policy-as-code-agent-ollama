import React, { useState, useEffect } from 'react'
import axios from 'axios'
import './App.css'
import Tabs from './components/Tabs'
import Monitoring from './components/Monitoring'
import RequirementsPanel from './components/RequirementsPanel'

// dynamic imports for heavy libs will be used in functions below

function App() {

  const [existingPolicy, setExistingPolicy] = useState('')
  const [newRegulation, setNewRegulation] = useState('')
  const [response, setResponse] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [parsed, setParsed] = useState(null)
  const [reqsRemote, setReqsRemote] = useState(null)
  const [gapRemote, setGapRemote] = useState(null)
  const [draftsRemote, setDraftsRemote] = useState(null)
  const [codeSpecsRemote, setCodeSpecsRemote] = useState(null)
  const [summaryRemote, setSummaryRemote] = useState(null)
  const [activeTab, setActiveTab] = useState('monitoring')
  const [localRequirements, setLocalRequirements] = useState([])
  const [tickets, setTickets] = useState([])

  // load persisted tickets when entering requirements tab
  React.useEffect(() => {
    if (activeTab === 'requirements') {
      axios.get('http://localhost:8080/api/policy/tickets')
        .then(res => setTickets(res.data || []))
        .catch(() => {})
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
        {
          existingPolicy,
          newRegulation
        },
        { timeout: 300000 } // 5 minute timeout for LLM processing
      )
      // Store raw JSON string for display
      setResponse(typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2))
      // Also attempt to parse into an object for structured UI
      try {
        const obj = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
        setParsed(obj)
      } catch (e) {
        setParsed(null)
      }
    } catch (err) {
      if (err.code === 'ECONNABORTED') {
        setError('Request timeout. Ensure Ollama is running and the model is loaded.')
      } else if (err.response?.status === 500) {
        setError('Server error. Check that Ollama is running: ollama serve')
      } else if (!err.response) {
        setError('Cannot connect to backend. Ensure it is running on http://localhost:8080')
      } else {
        setError(`Error: ${err.response?.data?.message || err.message}`)
      }
    } finally {
      setLoading(false)
    }
  }

  const fetchEndpoint = async (path, setter) => {
    if (!existingPolicy.trim() || !newRegulation.trim()) {
      setError('Both policy and regulation fields are required')
      return
    }

    setter(null)
    setError('')
    try {
      const res = await axios.post(
        `http://localhost:8080/api/policy/${path}`,
        { existingPolicy, newRegulation },
        { timeout: 300000 }
      )
      setter(res.data)
    } catch (err) {
      setError(`Failed to fetch ${path}: ${err.message}`)
    }
  }

  const handleNewRegulation = (item) => {
    // when monitoring discovers a new regulation item, call backend analyze to get structured output
    const text = item.description || item.title || ''
    // fire-and-forget - reuse analyze endpoint
    axios.post('http://localhost:8080/api/policy/analyze', { existingPolicy: '', newRegulation: text })
      .then(res => {
        const raw = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
        setParsed(raw)
        setLocalRequirements(raw.requirements || [])
      }).catch(err => {
        // ignore errors for monitoring
      })
  }

  const handleRequirementsUpdate = (updated) => {
    setLocalRequirements(updated)
  }

  const generateTicket = (req) => {
    // If this is a server-created ticket object, append it to the list
    if (req && req.id && req.createdAt) {
      setTickets(prev => [req, ...prev])
      return
    }
    // fallback: create a simple local ticket and persist via backend
    const payload = { requirementId: req.id, summary: req.text, recommendation: req.recommendation }
    axios.post('http://localhost:8080/api/policy/tickets', payload)
      .then(res => setTickets(prev => [res.data, ...prev]))
      .catch(() => {
        const ticket = { id: `T-${Date.now()}`, requirementId: req.id, summary: req.text, recommendation: req.recommendation, createdAt: new Date().toISOString() }
        setTickets(prev => [ticket, ...prev])
      })
  }

  const copyJSON = async () => {
    if (!response) return
    try {
      await navigator.clipboard.writeText(response)
      alert('JSON copied to clipboard')
    } catch (e) {
      alert('Copy failed')
    }
  }

  const downloadJSON = () => {
    if (!response) return
    const blob = new Blob([response], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'policy-analysis.json'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }

  const exportDrafts = () => {
    if (!parsed || !Array.isArray(parsed.policyDrafts) || parsed.policyDrafts.length === 0) {
      alert('No drafts to export')
      return
    }
    const combined = parsed.policyDrafts.map(d => `Requirement ${d.requirementId}\n\n${d.draft}\n\n---\n`).join('\n')
    const blob = new Blob([combined], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'policy-drafts.txt'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }

  const exportPDF = async () => {
    if (!parsed) { alert('No content to export'); return }
    try {
      const res = await fetch('http://localhost:8080/api/policy/export/pdf', {
        method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(parsed)
      })
      if (!res.ok) throw new Error('Export failed')
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'policy-drafts.pdf'
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch (e) {
      alert('PDF export failed: ' + e.message)
    }
  }

  const exportDOCX = async () => {
    if (!parsed) { alert('No content to export'); return }
    try {
      const res = await fetch('http://localhost:8080/api/policy/export/docx', {
        method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(parsed)
      })
      if (!res.ok) throw new Error('Export failed')
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'policy-drafts.docx'
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch (e) {
      alert('DOCX export failed: ' + e.message)
    }
  }

  return (
    <div className="container">
      <header>
        <h1>Policy-as-Code Regulatory Change Agent</h1>
        <div className="subtitle">Detect regulatory changes, assess impact, draft policy updates, and generate executable compliance rules — fast and auditable.</div>
      </header>

      <div className="content">
        <div style={{gridColumn:'1 / -1', marginBottom:12}}>
          <Tabs tabs={[{key:'monitoring',title:'Monitoring'},{key:'impact',title:'Impact'},{key:'requirements',title:'Requirements'},{key:'gaps',title:'Gap Analysis'},{key:'drafts',title:'Drafting'},{key:'code',title:'Code Gen'}]} active={activeTab} onChange={setActiveTab} />
        </div>

        {activeTab === 'monitoring' && (
          <div style={{gridColumn:'1 / -1'}} className="card">
            <Monitoring onNewRegulation={handleNewRegulation} />
          </div>
        )}

        {activeTab === 'requirements' && (
          <div style={{gridColumn:'1 / -1'}}>
            <RequirementsPanel requirements={localRequirements} onUpdate={handleRequirementsUpdate} onGenerateTicket={generateTicket} />
            <div className="card" style={{marginTop:12}}>
              <h4>Generated Tickets</h4>
              <pre className="result">{tickets.length ? JSON.stringify(tickets, null, 2) : 'No tickets generated'}</pre>
            </div>
          </div>
        )}

        {activeTab === 'impact' && (
          <div style={{gridColumn:'1 / -1'}} className="card">
            <h3>Impact & Summary</h3>
            <pre className="result">{summaryRemote ? JSON.stringify(summaryRemote, null, 2) : (parsed?.summary ? JSON.stringify(parsed.summary, null,2) : 'Not available')}</pre>
          </div>
        )}

        {activeTab === 'gaps' && (
          <div style={{gridColumn:'1 / -1'}} className="card">
            <h3>Gap Analysis</h3>
            <pre className="result">{gapRemote ? JSON.stringify(gapRemote, null, 2) : (parsed?.gapReport ? JSON.stringify(parsed.gapReport, null,2) : 'Not available')}</pre>
          </div>
        )}

        {activeTab === 'drafts' && (
          <div style={{gridColumn:'1 / -1'}} className="card">
            <h3>Policy Drafts</h3>
            <pre className="result">{draftsRemote ? JSON.stringify(draftsRemote, null, 2) : (parsed?.policyDrafts ? JSON.stringify(parsed.policyDrafts, null,2) : 'Not available')}</pre>
          </div>
        )}

        {activeTab === 'code' && (
          <div style={{gridColumn:'1 / -1'}} className="card">
            <h3>Code Specifications</h3>
            <pre className="result">{codeSpecsRemote ? JSON.stringify(codeSpecsRemote, null, 2) : (parsed?.codeSpecifications ? JSON.stringify(parsed.codeSpecifications, null,2) : 'Not available')}</pre>
          </div>
        )}
        <div className="input-section">
          <div className="input-group">
            <label htmlFor="existing-policy">Existing Policy</label>
            <textarea
              id="existing-policy"
              rows="8"
              placeholder="Paste your current YAML policy here..."
              value={existingPolicy}
              onChange={(e) => setExistingPolicy(e.target.value)}
              disabled={loading}
            />
          </div>

          <div className="input-group">
            <label htmlFor="new-regulation">New Regulation</label>
            <textarea
              id="new-regulation"
              rows="8"
              placeholder="Paste the new regulation or compliance requirement here..."
              value={newRegulation}
              onChange={(e) => setNewRegulation(e.target.value)}
              disabled={loading}
            />
          </div>

          <button
            onClick={analyze}
            disabled={loading}
            className="analyze-btn"
          >
            {loading ? 'Analyzing... (This may take a moment)' : 'Analyze'}
          </button>
        </div>

        {error && <div className="error">{error}</div>}

        {parsed ? (
          <div className="output-section">
            <h3>Structured Analysis</h3>
            <div className="toolbar">
              <button className="btn" onClick={copyJSON}>Copy JSON</button>
              <button className="btn" onClick={downloadJSON}>Download JSON</button>
              <button className="btn" onClick={exportDrafts}>Export Drafts</button>
              <button className="btn primary" onClick={() => { navigator.clipboard?.writeText(JSON.stringify(parsed.summary || {}, null, 2)); alert('Summary copied') }}>Copy Summary</button>
              <button className="btn" onClick={() => fetchEndpoint('requirements', setReqsRemote)}>Fetch Requirements</button>
              <button className="btn" onClick={() => fetchEndpoint('gap-report', setGapRemote)}>Fetch Gap Report</button>
              <button className="btn" onClick={() => fetchEndpoint('policy-drafts', setDraftsRemote)}>Fetch Drafts</button>
              <button className="btn" onClick={() => fetchEndpoint('code-specs', setCodeSpecsRemote)}>Fetch Code Specs</button>
              <button className="btn" onClick={() => fetchEndpoint('summary', setSummaryRemote)}>Fetch Summary</button>
              <button className="btn" onClick={exportPDF}>Export PDF</button>
              <button className="btn" onClick={exportDOCX}>Export DOCX</button>
            </div>
            <div className="summary">
              <strong>Summary:</strong>
              <div>Total requirements: {parsed.summary?.totalRequirements ?? '—'}</div>
              <div>Already satisfied: {parsed.summary?.alreadySatisfied ?? '—'}</div>
              <div>Policy updates needed: {parsed.summary?.policyUpdatesNeeded ?? '—'}</div>
              <div>New controls needed: {parsed.summary?.newControlsNeeded ?? '—'}</div>
              <div>System implementations needed: {parsed.summary?.systemImplementationsNeeded ?? '—'}</div>
            </div>

            <div className="requirements">
              <h4>Requirements</h4>
              {Array.isArray(parsed.requirements) && parsed.requirements.length > 0 ? (
                parsed.requirements.map((r) => (
                  <div key={r.id} className="requirement">
                    <div><strong>#{r.id}</strong> — {r.text}</div>
                    <div>Type: {r.type} | Satisfied: {String(r.satisfied)}</div>
                    <div>Recommendation: {r.recommendation}</div>
                    {Array.isArray(r.tests) && (
                      <div>Tests: {r.tests.join('; ')}</div>
                    )}
                  </div>
                ))
              ) : (
                <div>No structured requirements found.</div>
              )}
            </div>

            <div className="gap-report">
              <h4>Gap Report</h4>
              {Array.isArray(parsed.gapReport) && parsed.gapReport.length > 0 ? (
                parsed.gapReport.map((g, i) => (
                  <div key={i} className="gap">
                    <div><strong>Req #{g.requirementId}:</strong> {g.issue}</div>
                    <div>{g.detail}</div>
                  </div>
                ))
              ) : (
                <div>No gaps reported.</div>
              )}
            </div>

            <div className="drafts">
              <h4>Policy Drafts</h4>
              {Array.isArray(parsed.policyDrafts) && parsed.policyDrafts.length > 0 ? (
                parsed.policyDrafts.map((d, i) => (
                  <div key={i} className="draft">
                    <div><strong>Req #{d.requirementId}</strong></div>
                    <pre className="draft-text">{d.draft}</pre>
                  </div>
                ))
              ) : (
                <div>No drafts generated.</div>
              )}
            </div>

            <div className="code-specs">
              <h4>Code Specifications</h4>
              {Array.isArray(parsed.codeSpecifications) && parsed.codeSpecifications.length > 0 ? (
                parsed.codeSpecifications.map((c, i) => (
                  <div key={i} className="code-spec">
                    <div><strong>Req #{c.requirementId}</strong></div>
                    <pre className="spec">{c.spec}</pre>
                  </div>
                ))
              ) : (
                <div>No code specifications produced.</div>
              )}
            </div>

            <hr />
            <h4>Raw JSON</h4>
            <pre className="result">{response}</pre>
            <div className="card">
              <h4>Remote Endpoint Results</h4>
              <div>
                <strong>Requirements:</strong>
                <pre className="result">{reqsRemote ? JSON.stringify(reqsRemote, null, 2) : 'Not fetched'}</pre>
              </div>
              <div>
                <strong>Gap Report:</strong>
                <pre className="result">{gapRemote ? JSON.stringify(gapRemote, null, 2) : 'Not fetched'}</pre>
              </div>
              <div>
                <strong>Policy Drafts:</strong>
                <pre className="result">{draftsRemote ? JSON.stringify(draftsRemote, null, 2) : 'Not fetched'}</pre>
              </div>
              <div>
                <strong>Code Specs:</strong>
                <pre className="result">{codeSpecsRemote ? JSON.stringify(codeSpecsRemote, null, 2) : 'Not fetched'}</pre>
              </div>
              <div>
                <strong>Summary:</strong>
                <pre className="result">{summaryRemote ? JSON.stringify(summaryRemote, null, 2) : 'Not fetched'}</pre>
              </div>
            </div>
          </div>
        ) : (
          response && (
            <div className="output-section">
              <h3>Analysis Result</h3>
              <pre className="result">{response}</pre>
            </div>
          )
        )}
      </div>
    </div>
  )
}

export default App
