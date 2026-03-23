import { createMap } from 'svg-dotted-map'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const outPath = path.join(__dirname, '..', 'static', 'dotted-map-bg.svg')

const width = 300
const height = 150
const mapSamples = 5600
const dotRadius = 0.36
const dotColor = '#8a8a8a'

const { points } = createMap({ width, height, mapSamples, radius: dotRadius })

const sorted = [...points].sort((a, b) => a.y - b.y || a.x - b.x)
const rowMap = new Map()
let step = 0
let prevY = Number.NaN
let prevXInRow = Number.NaN

for (const p of sorted) {
  if (p.y !== prevY) {
    prevY = p.y
    prevXInRow = Number.NaN
    if (!rowMap.has(p.y)) rowMap.set(p.y, rowMap.size)
  }
  if (!Number.isNaN(prevXInRow)) {
    const delta = p.x - prevXInRow
    if (delta > 0) step = step === 0 ? delta : Math.min(step, delta)
  }
  prevXInRow = p.x
}

const xStep = step || 1
const yToRowIndex = rowMap

const circles = points
  .map((point, index) => {
    const rowIndex = yToRowIndex.get(point.y) ?? 0
    const offsetX = rowIndex % 2 === 1 ? xStep / 2 : 0
    const cx = point.x + offsetX
    const cy = point.y
    return `<circle cx="${cx.toFixed(3)}" cy="${cy.toFixed(3)}" r="${dotRadius}" fill="${dotColor}"/>`
  })
  .join('')

const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" preserveAspectRatio="xMidYMid slice">${circles}</svg>
`

fs.writeFileSync(outPath, svg, 'utf8')
console.log('Wrote', outPath, `(${points.length} dots)`)
