import React, { useState, useEffect, useRef } from 'react'

export default function Monitoring({onNewRegulation}){
  const [feeds, setFeeds] = useState([])
  const [url, setUrl] = useState('')
  const [items, setItems] = useState([])
  const timerRef = useRef(null)

  useEffect(() => {
    return () => clearInterval(timerRef.current)
  }, [])

  const addFeed = () => {
    if (!url.trim()) return
    setFeeds(prev => [...prev, url.trim()])
    setUrl('')
  }

const fetchFeeds = async () => {
  for (const f of feeds) {
    try {
      const res = await fetch('http://localhost:8080/api/policy/fetch-feed', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: f }),
      });

      const json = await res.json();
      if (!Array.isArray(json)) continue;

      setItems(prev => {
        const updated = [...prev];
        json.forEach(e => {
          if (!updated.some(it => it.link && it.link === e.link)) {
            updated.unshift(e);
            onNewRegulation?.(e);
          }
        });
        return updated;
      });

    } catch (err) {
      console.error(err);
    }
  }
};

  const startPolling = (ms=1000*60*5) => {
    clearInterval(timerRef.current)
    timerRef.current = setInterval(fetchFeeds, ms)
  }

  return (
    <div>
      <div style={{display:'flex', gap:8, marginBottom:8}}>
        <input value={url} onChange={e=>setUrl(e.target.value)} placeholder="RSS/Atom feed URL (CORS may block)" style={{flex:1,padding:8}} />
        <button className="btn" onClick={addFeed}>Add</button>
        <button className="btn" onClick={() => fetchFeeds()}>Fetch Now</button>
        <button className="btn primary" onClick={() => startPolling()}>Start Polling (5m)</button>
      </div>

      <div style={{marginBottom:12}}>
        <strong>Monitored feeds:</strong>
        <ul>
          {feeds.map((f,i)=> <li key={i}><a href={f} target="_blank" rel="noreferrer">{f}</a></li>)}
        </ul>
      </div>

      <div>
        <strong>Recent items:</strong>
        <div>
          {items.map((it,i)=> (
            <div key={i} className="card" style={{marginBottom:8}}>
              <div style={{display:'flex',justifyContent:'space-between'}}>
                <div style={{fontWeight:700}}>{it.title}</div>
                {it.link && <a href={it.link} target="_blank" rel="noreferrer">Open</a>}
              </div>
              <div style={{marginTop:6}}>{it.description?.slice(0,400)}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
