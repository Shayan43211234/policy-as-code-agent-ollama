import React, { useState, useEffect, useRef } from 'react'

  export default function Monitoring({
    feeds,
    setFeeds,
    items,
    setItems,
    onNewRegulation,
    loading= false,
    setLoading
  }) {

  //const [feeds, setFeeds] = useState([])
  const [url, setUrl] = useState('')
  const [polling,setPolling] = useState(false)
  //const [items, setItems] = useState([])
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

  const fetchFeeds = async () => {
    try{
      setLoading(true);
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
                detectedAt: new Date().toISOString()
              }

              updated.unshift(item)

              // 🔹 send FULL item including analysis
              if (feedItem.analysis) {
                onNewRegulation?.(item)
              }
            }

          })

          return updated

        })

      } catch (err) {

        console.error("RSS fetch error:", err)

      }

    }
    }finally{
      setLoading(false);
    }
  }

  const startPolling = (ms=1000*60*2) => {

    clearInterval(timerRef.current)

    fetchFeeds()

    timerRef.current = setInterval(fetchFeeds, ms)

    setPolling(true)
  }

  return (

    <div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>

        <input
          value={url}
          onChange={e => setUrl(e.target.value)}
          placeholder="RSS / Atom feed URL"
          style={{ flex: 1, padding: 8 }}
        />

        <button className="btn" onClick={addFeed} disabled={loading}>
          Add
        </button>

        <button className="btn" onClick={fetchFeeds} disabled={loading}>
          Fetch Now
        </button>

        {/* <button
          className="btn primary"
          onClick={() => startPolling()}
          disabled={loading}
        >
          {polling ? "Polling Active (2m)" : "Start Polling (2m)"}
        </button> */}

        <button
          className="btn"
          onClick={() => {
            setFeeds([])
            setItems([])
          }}
        >
          Clear
        </button>

      </div>

      <div style={{ marginBottom: 12 }}>
        <strong>Monitored feeds:</strong>

        <ul>
          {feeds.map((f, i) => (
            <li key={i}>
              <a href={f} target="_blank" rel="noreferrer">{f}</a>
            </li>
          ))}
        </ul>
      </div>

      <div>

        <strong>Recent items:</strong>

        <div>

          {items.map((it, i) => (

            <div key={i} className="card" style={{ marginBottom: 8 }}>

              <div style={{ display: 'flex', justifyContent: 'space-between' }}>

                <div style={{ fontWeight: 700 }}>
                  {it.title}
                </div>

                {it.link &&
                  <a href={it.link} target="_blank" rel="noreferrer">
                    Open
                  </a>
                }

              </div>

              <div style={{ marginTop: 6, fontSize: 12, color: '#666' }}>
                Detected at: {new Date(it.detectedAt).toLocaleString()}
              </div>

            </div>

          ))}

        </div>

      </div>

    </div>

  )

}