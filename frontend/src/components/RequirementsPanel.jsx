import React, { useState, useEffect } from 'react'

export default function RequirementsPanel({requirements, onUpdate, onGenerateTicket}){
  const [local, setLocal] = useState([])

  useEffect(()=>{
    setLocal(requirements?.map(r => ({...r, _status: r.satisfied ? 'accepted' : 'pending'})) || [])
  },[requirements])

  const setField = (id, field, value) => {
    setLocal(prev => prev.map(r => r.id===id ? {...r, [field]: value} : r))
  }

  const accept = (id) => setLocal(prev => prev.map(r => r.id===id ? {...r, _status:'accepted'} : r))
  const reject = (id) => setLocal(prev => prev.map(r => r.id===id ? {...r, _status:'rejected'} : r))

  const persistTicket = async (r) => {
    try {
      const res = await fetch('http://localhost:8080/api/policy/tickets', {
        method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({ requirementId: r.id, summary: r.text, recommendation: r.recommendation })
      })
      const json = await res.json()
      onGenerateTicket?.(json)
    } catch (e) {
      alert('Failed to create ticket: ' + e.message)
    }
  }

  useEffect(() => {
    if (onUpdate) onUpdate(local);
  }, [local]);

  return (
    <div>
      {local.length===0 && <div className="card">No requirements available.</div>}
      {local.map(r=> (
        <div key={r.id} className="requirement">
          <div style={{display:'flex',justifyContent:'space-between',alignItems:'center'}}>
            <div><strong>#{r.id}</strong> — <input style={{width:'70%'}} value={r.text} onChange={(e)=>setField(r.id,'text',e.target.value)} /></div>
            <div>
              <span className="badge">{r.type}</span>
              <button className="btn" onClick={()=>accept(r.id)}>Accept</button>
              <button className="btn" onClick={()=>reject(r.id)}>Reject</button>
              <button className="btn primary" onClick={()=>persistTicket(r)}>Generate Ticket</button>
            </div>
          </div>
          <div style={{marginTop:8}}>Recommendation: <select value={r.recommendation} onChange={(e)=>setField(r.id,'recommendation',e.target.value)}>
            <option value="no_action">no_action</option>
            <option value="update_policy">update_policy</option>
            <option value="add_control">add_control</option>
            <option value="implement_system_rule">implement_system_rule</option>
          </select></div>
          <div style={{marginTop:8}}>Tests: <input style={{width:'100%'}} value={(r.tests||[]).join('; ')} onChange={(e)=>setField(r.id,'tests',e.target.value.split(';').map(s=>s.trim()))} /></div>
          <div style={{marginTop:8}}>Status: <strong>{r._status}</strong></div>
        </div>
      ))}
    </div>
  )
}
