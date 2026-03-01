import React from 'react'

export default function Tabs({tabs, active, onChange}){
  return (
    <div style={{display:'flex', gap:8, marginBottom:12}}>
      {tabs.map(t => (
        <button
          key={t.key}
          onClick={() => onChange(t.key)}
          className={`btn ${active===t.key ? 'primary' : ''}`}
          style={{padding:'8px 14px'}}
        >
          {t.title}
        </button>
      ))}
    </div>
  )
}
