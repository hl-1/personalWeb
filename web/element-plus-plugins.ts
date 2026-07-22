import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export function elementPlusPlugins(generateTypes: boolean) {
  const resolver = ElementPlusResolver({ importStyle: generateTypes ? 'css' : false })
  return [
    AutoImport({
      resolvers: [resolver],
      dts: generateTypes ? 'src/auto-imports.d.ts' : false,
    }),
    Components({
      resolvers: [resolver],
      dts: generateTypes ? 'src/components.d.ts' : false,
    }),
  ]
}
