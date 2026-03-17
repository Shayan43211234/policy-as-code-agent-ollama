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

      setResponse(typeof res.data === 'string'
        ? res.data
        : JSON.stringify(res.data, null, 2))

      const obj = typeof res.data === 'string'
        ? JSON.parse(res.data)
        : res.data

      setParsed(obj)
      setLocalRequirements(obj.requirements || [])
      setActiveTab('impact')

    } catch (err) {

      if (err.code === 'ECONNABORTED') {
        setError('Request timeout. Ensure Ollama is running.')
      } else if (!err.response) {
        setError('Cannot connect to backend.')
      } else {
        setError(err.message)
      }

    } finally {
      setLoading(false)
    }
  }

  const handleNewRegulation = (item) => {

    const link = item.link

    if (!item.analysis) return

    if (processedLinksRef.current.has(link)) {
      return
    }

    processedLinksRef.current.add(link)

    const analysis = item.analysis

    setNotification("New regulatory update detected and analyzed")

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
        console.error("Ticket creation failed", err)
        alert("Ticket creation failed")
      })
  }

  const copyJSON = async () => {
    if (!response) return
    await navigator.clipboard.writeText(response)
    alert('JSON copied')
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

    if (!parsed?.policyDrafts?.length) {
      alert('No drafts to export')
      return
    }

    const combined = parsed.policyDrafts
      .map(d => `Requirement ${d.requirementId}\n\n${d.draft}\n\n---`)
      .join('\n')

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

    await fetch(`http://localhost:8080/api/policy/${id}/${action}`, {
      method: 'POST'
    })

    loadUpdates()
  }

  return (
    <div className="container">

    {loading && (
      <div className="overlay">
        <div className="loaderBox">
          Processing request...
        </div>
      </div>
    )}
      <header>
        <h1>Policy-as-Code Regulatory Change Agent</h1>
      </header>

      {notification && (
        <div
          style={{
            background: '#e6f7ff',
            border: '1px solid #91d5ff',
            padding: '10px',
            marginBottom: '12px',
            borderRadius: '4px'
          }}
        >
          ⚠️ {notification}
        </div>
      )}

      <div className="content">

        <div style={{gridColumn:'1 / -1', marginBottom:12}}>
          <Tabs
            tabs={[
              {key:'monitoring',title:'Monitoring'},
              {key:'impact',title:'Impact'},
              {key:'requirements',title:'Requirements'},
              {key:'gaps',title:'Gap Analysis'},
              {key:'drafts',title:'Drafting'},
              {key:'code',title:'Code Gen'},
              {key:'workflow',title:'Workflow'}
            ]}
            active={activeTab}
            onChange={setActiveTab}
          />
        </div>

        {activeTab === 'monitoring' &&
          <div className="card" style={{gridColumn:'1 / -1'}}>
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

        {activeTab === 'impact' &&
          <div className="card" style={{gridColumn:'1 / -1'}}>
            <h3>Impact Summary</h3>
            <pre className="result">
              {parsed?.summary
                ? JSON.stringify(parsed.summary, null, 2)
                : 'Run analysis to view impact'}
            </pre>
          </div>
        }

        {activeTab === 'requirements' &&
          <div style={{gridColumn:'1 / -1'}}>
            <RequirementsPanel
              requirements={localRequirements.length ? localRequirements : (parsed?.requirements || [])}
              onUpdate={handleRequirementsUpdate}
              onGenerateTicket={generateTicket}
            />
            <div className="card" style={{marginTop:12}}>
              <h4>Generated Tickets</h4>
              <pre className="result">
                {tickets.length
                  ? JSON.stringify(tickets,null,2)
                  : 'No tickets generated'}
              </pre>
            </div>
          </div>
        }

        {activeTab === 'gaps' &&
          <div className="card" style={{gridColumn:'1 / -1'}}>
            <h3>Gap Analysis</h3>
            <pre className="result">
              {parsed?.gapReport
                ? JSON.stringify(parsed.gapReport,null,2)
                : 'Run analysis to view gaps'}
            </pre>
          </div>
        }

        {activeTab === 'drafts' &&
          <div className="card" style={{gridColumn:'1 / -1'}}>
            <h3>Policy Drafts</h3>
            <pre className="result">
              {parsed?.policyDrafts
                ? JSON.stringify(parsed.policyDrafts,null,2)
                : 'Run analysis to view drafts'}
            </pre>
          </div>
        }

        {activeTab === 'code' &&
          <div className="card" style={{gridColumn:'1 / -1'}}>
            <h3>Code Specifications</h3>
            <pre className="result">
              {parsed?.codeSpecifications
                ? JSON.stringify(parsed.codeSpecifications,null,2)
                : 'Run analysis to view code rules'}
            </pre>
          </div>
        }

        {activeTab === 'workflow' &&
          <div className="card" style={{gridColumn:'1 / -1'}}>
            <h3>Regulatory Workflow</h3>

            {updates.map(u => (
              <div key={u.id} style={{marginBottom:16}}>
                <div>ID: {u.id}</div>
                <div>Status: {u.status}</div>

                {(allowedMap[u.id] || []).map(a => {

                  const actionMap = {
                    REVIEW_PENDING:'submit-review',
                    APPROVED:'approve',
                    IMPLEMENTED:'mark-implemented',
                    CLOSED:'close'
                  }

                  return (
                    <button
                      key={a}
                      className="btn"
                      onClick={()=>transitionUpdate(u.id, actionMap[a])}
                    >
                      {a}
                    </button>
                  )
                })}
              </div>
            ))}

          </div>
        }

        <div className="input-section">

          <div className="input-group">
            <label>Existing Policy</label>
            <textarea
              rows="6"
              placeholder={`Example:
policy:
  name: VendorRiskPolicy
  reviewFrequencyDays: 180`}
              value={existingPolicy}
              onChange={e=>setExistingPolicy(e.target.value)}
            />
          </div>

          <div className="input-group">
            <label>New Regulation</label>
            <textarea
              rows="6"
              placeholder={`Example:
All critical vendors must undergo a risk assessment every 180 days.`}
              value={newRegulation}
              onChange={e=>setNewRegulation(e.target.value)}
            />
          </div>

          <button
            className="analyze-btn"
            disabled={loading}
            onClick={analyze}
          >
            {loading ? 'Analyzing...' : 'Analyze'}
          </button>

        </div>

        {error && <div className="error">{error}</div>}

      </div>
    </div>
  )
}

export default App