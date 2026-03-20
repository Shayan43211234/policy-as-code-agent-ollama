import React, { useState, useEffect, useRef } from 'react'

export default function Monitoring({
  feeds,
  setFeeds,
  items,
  setItems,
  onNewRegulation,
  loading = false,
  setLoading
}) {

  const [url, setUrl] = useState('')
  const [polling, setPolling] = useState(false)
  const [pollingInterval, setPollingInterval] = useState(null)
  const timerRef = useRef(null)

  useEffect(() => {
    return () => clearInterval(timerRef.current)
  }, [])

  const addFeed = () => {
    const trimmed = url.trim()
    if (!trimmed) return
    setFeeds(prev => {
      if (prev.includes(trimmed)) return prev
      return [...prev, trimmed]
    })
    setUrl('')
  }

  const removeFeed = (f) => {
    setFeeds(prev => prev.filter(x => x !== f))
  }

  const fetchFeeds = async () => {
    if (!feeds.length) return
    try {
      setLoading(true)
      for (const f of [...feeds]) {
        try {
          const res = await fetch('http://localhost:8080/api/policy/fetch-feed', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url: f })
          })
          const json = await res.json()
          if (!Array.isArray(json)) continue
          setItems(prev => {
            const updated = [...prev]
            json.forEach(feedItem => {
              const exists = updated.some(
                it => it.link === feedItem.link || it.title === feedItem.title
              )
              if (!exists) {
                const item = {
                  ...feedItem,
                  detectedAt: new Date().toISOString(),
                  feedSource: f
                }
                updated.unshift(item)
                if (feedItem.analysis) {
                  onNewRegulation?.(item)
                }
              }
            })
            return updated
          })
        } catch (err) {
          console.error('RSS fetch error:', err)
        }
      }
    } finally {
      setLoading(false)
    }
  }

  const startPolling = (ms = 1000 * 60 * 2) => {
    clearInterval(timerRef.current)
    fetchFeeds()
    timerRef.current = setInterval(fetchFeeds, ms)
    setPolling(true)
  }

  const stopPolling = () => {
    clearInterval(timerRef.current)
    setPolling(false)
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') addFeed()
  }

  return (
    <div className="monitoring-wrap">

      <div className="monitoring-controls">
        <input
          value={url}
          onChange={e => setUrl(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="RSS / Atom feed URL (e.g. https://www.occ.gov/rss/...)"
          className="feed-input"
          disabled={loading}
        />
        <button className="btn" onClick={addFeed} disabled={loading || !url.trim()}>
          + Add
        </button>
        <button className="btn" onClick={fetchFeeds} disabled={loading || !feeds.length}>
          {loading ? '⏳ Fetching...' : '↻ Fetch Now'}
        </button>
        <button
          className={`btn ${polling ? 'danger' : 'primary'}`}
          onClick={polling ? stopPolling : startPolling}
          disabled={loading}
        >
          {polling ? '⏹ Stop Polling' : '▶ Start Polling (2m)'}
        </button>
        <button
          className="btn"
          onClick={() => { setFeeds([]); setItems([]) }}
          disabled={loading}
        >
          Clear All
        </button>
      </div>

      <div className="monitoring-body">

        <div className="feeds-panel">
          <div className="panel-title">
            📡 Monitored Feeds
            {polling && <span className="polling-indicator">● Live</span>}
          </div>
          {feeds.length === 0 ? (
            <div className="feeds-empty">No feeds added yet. Paste an RSS/Atom URL above.</div>
          ) : (
            <ul className="feeds-list">
              {feeds.map((f, i) => (
                <li key={i} className="feed-item">
                  <a href={f} target="_blank" rel="noreferrer" className="feed-link" title={f}>
                    {f.length > 60 ? f.substring(0, 60) + '...' : f}
                  </a>
                  <button className="remove-feed" onClick={() => removeFeed(f)}>✕</button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="items-panel">
          <div className="panel-title">
            🗞 Recent Regulatory Items
            {items.length > 0 && <span className="items-count">{items.length} items</span>}
          </div>
          {items.length === 0 ? (
            <div className="feeds-empty">
              No items fetched yet. Add a feed URL and click Fetch Now.
              <br /><br />
              <strong>Example feeds:</strong><br />
              • https://www.occ.gov/rss/occ-news.xml<br />
              • https://www.fdic.gov/news/financial-institution-letters/rss.xml
            </div>
          ) : (
            <div className="items-list">
              {items.map((it, i) => (
                <div key={i} className="feed-item-card">
                  <div className="feed-item-header">
                    <div className="feed-item-title">{it.title}</div>
                    <div className="feed-item-meta">
                      {it.analysis && (
                        <span className="analyzed-badge">✅ Analyzed</span>
                      )}
                      {it.link && (
                        <a href={it.link} target="_blank" rel="noreferrer" className="feed-open-link">
                          Open ↗
                        </a>
                      )}
                    </div>
                  </div>
                  {it.description && (
                    <div className="feed-item-desc">
                      {it.description.length > 200
                        ? it.description.substring(0, 200) + '...'
                        : it.description}
                    </div>
                  )}
                  <div className="feed-item-footer">
                    <span>🕐 {new Date(it.detectedAt).toLocaleString()}</span>
                    {it.feedSource && (
                      <span className="feed-source-tag">
                        {new URL(it.feedSource).hostname}
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

      </div>
    </div>
  )
}
