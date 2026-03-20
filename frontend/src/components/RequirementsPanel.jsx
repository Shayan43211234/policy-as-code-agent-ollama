import React, { useState, useEffect } from 'react'

export default function RequirementsPanel({ requirements, onGenerateTicket }) {

  const [local, setLocal] = useState([])
  const [ticketedIds, setTicketedIds] = useState(new Set())
  const [filter, setFilter] = useState('all')

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
      setTicketedIds(prev => new Set([...prev, r.dbId || r.id]))
      onGenerateTicket?.(json)
    } catch (e) {
      alert('Failed to create ticket: ' + e.message)
    }
  }

  const recommendationColors = {
    no_action: '#10b981',
    update_policy: '#3b82f6',
    add_control: '#f59e0b',
    implement_system_rule: '#8b5cf6'
  }

  const recommendationLabels = {
    no_action: 'No Action',
    update_policy: 'Update Policy',
    add_control: 'Add Control',
    implement_system_rule: 'Implement System Rule'
  }

  const filtered = filter === 'all'
    ? local
    : filter === 'satisfied'
      ? local.filter(r => r.satisfied)
      : local.filter(r => !r.satisfied)

  return (
    <div className="card">
      <div className="section-header">
        <h3 className="section-title">📋 Requirements</h3>
        <div className="filter-tabs">
          {['all', 'satisfied', 'unsatisfied'].map(f => (
            <button
              key={f}
              className={`filter-btn ${filter === f ? 'active' : ''}`}
              onClick={() => setFilter(f)}
            >
              {f === 'all' ? `All (${local.length})`
                : f === 'satisfied' ? `✅ Satisfied (${local.filter(r => r.satisfied).length})`
                : `❌ Gaps (${local.filter(r => !r.satisfied).length})`}
            </button>
          ))}
        </div>
      </div>

      {filtered.length === 0 && (
        <div className="empty-state">
          <span>📋</span>
          <p>No requirements available. Run an analysis first.</p>
        </div>
      )}

      <div className="requirements-list">
        {filtered.map((r, i) => {
          const reqKey = r.dbId || r.id
          const isTicketed = ticketedIds.has(reqKey)
          const recColor = recommendationColors[r.recommendation] || '#6b7280'

          return (
            <div key={i} className={`req-card ${r.satisfied ? 'req-satisfied' : 'req-gap'}`}>

              <div className="req-header">
                <div className="req-id-group">
                  <span className="req-number">#{r.id}</span>
                  <span className={`satisfied-badge ${r.satisfied ? 'yes' : 'no'}`}>
                    {r.satisfied ? '✅ Satisfied' : '❌ Gap'}
                  </span>
                  <span className="type-badge">{r.type}</span>
                </div>
                <div className="req-actions">
                  <button
                    className={`btn ${isTicketed ? 'ticketed' : 'primary'}`}
                    onClick={() => !isTicketed && persistTicket(r)}
                    disabled={isTicketed || !r.dbId}
                    title={!r.dbId ? 'Save analysis first to generate ticket' : ''}
                  >
                    {isTicketed ? '🎫 Ticketed' : '+ Create Ticket'}
                  </button>
                </div>
              </div>

              <div className="req-text">{r.text}</div>

              <div className="req-meta">
                <div className="req-meta-item">
                  <span className="meta-label">Recommendation</span>
                  <span
                    className="rec-badge"
                    style={{ background: recColor + '22', color: recColor, border: `1px solid ${recColor}44` }}
                  >
                    {recommendationLabels[r.recommendation] || r.recommendation}
                  </span>
                </div>

                <div className="req-meta-item">
                  <span className="meta-label">Business Line</span>
                  <span className="meta-value business-line">{r.impactedBusinessLine || '—'}</span>
                </div>

                <div className="req-meta-item">
                  <span className="meta-label">System</span>
                  <span className="meta-value system-value">{r.impactedSystem || '—'}</span>
                </div>
              </div>

              {r.rationale && (
                <div className="req-rationale">
                  <span className="meta-label">Rationale: </span>{r.rationale}
                </div>
              )}

            </div>
          )
        })}
      </div>
    </div>
  )
}
