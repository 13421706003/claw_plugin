const API_BASE = import.meta.env.VITE_API_BASE

function getToken() {
  return localStorage.getItem('openhsd_token') || sessionStorage.getItem('openhsd_token') || ''
}

export async function request(url, options = {}) {
  const token = getToken()
  
  const headers = {
    ...options.headers,
  }
  
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  
  if (options.body && typeof options.body === 'string' && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json'
  }
  
  const res = await fetch(`${API_BASE}${url}`, {
    ...options,
    headers,
  })
  
  return res
}

export { API_BASE }
