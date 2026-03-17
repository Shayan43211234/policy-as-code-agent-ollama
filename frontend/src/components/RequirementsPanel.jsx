import React, { useState, useEffect } from 'react'

export default function RequirementsPanel({ requirements, onGenerateTicket }) {

  const [local, setLocal] = useState([])

  useEffect(() => {
    setLocal(requirements || [])
  }, [requirements])

  const persistTicket = async (r) => {
    try {
      const res = await fetch('http://localhost:8080/api/policy/tickets', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          requirementId: r.dbId,
          summary: r.text,
          recommendation: r.recommendation
        })
      })

      const json = await res.json()
      onGenerateTicket?.(json)

    } catch (e) {
      alert('Failed to create ticket: ' + e.message)
    }
  }

  return (
    <div>

      {local.length === 0 &&
        <div className="card">
          No requirements available.
        </div>
      }

      {local.map(r => (

        <div key={r.id} className="requirement">

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>

            <div>
              <strong>#{r.id}</strong> — {r.text}
            </div>

            <div>
              <span className="badge">{r.type}</span>
              <button
                className="btn primary"
                onClick={() => persistTicket(r)}
              >
                Generate Ticket
              </button>
            </div>

          </div>

          <div style={{ marginTop: 8 }}>
            <strong>Recommendation:</strong> {r.recommendation}
          </div>

          <div style={{ marginTop: 8 }}>
            <strong>Impacted Business Line:</strong> {r.impactedBusinessLine}
          </div>

          <div style={{ marginTop: 8 }}>
            <strong>Impacted System:</strong> {r.impactedSystem}
          </div>

        </div>

      ))}

    </div>
  )
}