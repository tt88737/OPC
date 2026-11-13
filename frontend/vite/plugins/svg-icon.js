import fs from 'fs'
import path from 'path'

const virtualModuleId = 'virtual:svg-icons-register'
const resolvedVirtualModuleId = `\0${virtualModuleId}`

function createSprite() {
  const iconDir = path.resolve(process.cwd(), 'src/assets/icons/svg')
  const symbols = fs.readdirSync(iconDir)
    .filter((file) => file.toLowerCase().endsWith('.svg'))
    .sort()
    .map((file) => {
      const name = path.basename(file, '.svg')
      const content = fs.readFileSync(path.join(iconDir, file), 'utf8')
        .replace(/<\?xml[^>]*>\s*/i, '')
        .replace(/<!DOCTYPE[^>]*>\s*/i, '')
        .replace(/<svg\b([^>]*)>/i, (_, attrs) => {
          const viewBox = attrs.match(/\bviewBox\s*=\s*(["'][^"']+["'])/i)
          if (viewBox) return '<symbol id="icon-' + name + '" viewBox=' + viewBox[1] + '>'
          const width = attrs.match(/\bwidth\s*=\s*["']([0-9.]+)["']/i)
          const height = attrs.match(/\bheight\s*=\s*["']([0-9.]+)["']/i)
          const fallback = width && height ? ' viewBox="0 0 ' + width[1] + ' ' + height[1] + '"' : ''
          return '<symbol id="icon-' + name + '"' + fallback + '>'
        })
        .replace(/<\/svg>\s*$/i, '</symbol>')
      return content
    })
  return symbols.join('')
}

export default function createSvgIcon() {
  return {
    name: 'opc-svg-icons',
    resolveId(id) {
      return id === virtualModuleId ? resolvedVirtualModuleId : undefined
    },
    load(id) {
      if (id !== resolvedVirtualModuleId) return undefined
      const sprite = JSON.stringify(createSprite())
      return `const sprite = ${sprite};\nconst container = document.createElement('div');\ncontainer.setAttribute('aria-hidden', 'true');\ncontainer.style.position = 'absolute';\ncontainer.style.width = '0';\ncontainer.style.height = '0';\ncontainer.style.overflow = 'hidden';\ncontainer.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg">' + sprite + '</svg>';\ndocument.body.appendChild(container);`
    },
    handleHotUpdate({ file, server }) {
      if (file.startsWith(path.resolve(process.cwd(), 'src/assets/icons/svg'))) {
        server.ws.send({ type: 'full-reload' })
      }
    }
  }
}
