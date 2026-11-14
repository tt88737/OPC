import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'
import autoImportGlobals from './.eslintrc-auto-import.json' with { type: 'json' }

export default [
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**', 'public/**']
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
        ...autoImportGlobals.globals
      }
    }
  },
  // Exact-file migration baseline for rules enabled by recommended and Vue essential.
  {
    files: ['src/utils/opc.js'],
    rules: {
      'no-undef': 'off',
      'no-unused-vars': 'off'
    }
  },
  {
    files: ['src/views/monitor/operlog/detail.vue'],
    rules: {
      'no-undef': 'off',
      'vue/multi-word-component-names': 'off'
    }
  },
  {
    files: [
      'src/directive/permission/hasPermi.js',
      'src/directive/permission/hasRole.js',
      'src/permission.js',
      'src/store/modules/dict.js',
      'src/store/modules/permission.js',
      'src/store/modules/tagsView.js',
      'src/utils/dict.js',
      'src/utils/generator/html.js',
      'src/utils/generator/render.js'
    ],
    rules: { 'no-unused-vars': 'off' }
  },
  {
    files: [
      'src/components/Editor/index.vue',
      'src/components/ExcelImportDialog/index.vue',
      'src/components/IconSelect/index.vue',
      'src/layout/components/InnerLink/index.vue',
      'src/layout/components/TagsView/index.vue',
      'src/layout/components/TopBar/index.vue',
      'src/layout/components/TopNav/index.vue',
      'src/layout/index.vue',
      'src/views/monitor/cache/list.vue',
      'src/views/monitor/job/detail.vue',
      'src/views/monitor/job/index.vue',
      'src/views/monitor/job/log.vue',
      'src/views/monitor/logininfor/index.vue',
      'src/views/monitor/operlog/index.vue',
      'src/views/system/config/index.vue',
      'src/views/system/dept/index.vue',
      'src/views/system/dict/data.vue',
      'src/views/system/dict/detail.vue',
      'src/views/system/dict/index.vue',
      'src/views/system/menu/index.vue',
      'src/views/system/notice/index.vue',
      'src/views/system/role/index.vue',
      'src/views/system/user/index.vue',
      'src/views/tool/build/index.vue',
      'src/views/tool/gen/index.vue'
    ],
    rules: {
      'no-unused-vars': 'off',
      'vue/multi-word-component-names': 'off'
    }
  },
  {
    files: ['src/components/DictTag/index.vue'],
    rules: {
      'no-unused-vars': 'off',
      'vue/multi-word-component-names': 'off',
      'vue/no-deprecated-filter': 'off',
      'vue/no-side-effects-in-computed-properties': 'off'
    }
  },
  {
    files: ['src/components/Crontab/index.vue'],
    rules: {
      'no-unused-vars': 'off',
      'vue/multi-word-component-names': 'off',
      'vue/no-dupe-keys': 'off'
    }
  },
  {
    files: ['src/components/RightToolbar/index.vue'],
    rules: {
      'no-unused-vars': 'off',
      'vue/multi-word-component-names': 'off',
      'vue/no-mutating-props': 'off'
    }
  },
  {
    files: ['src/views/register.vue'],
    rules: {
      'no-unused-vars': 'off',
      'vue/multi-word-component-names': 'off',
      'vue/no-ref-as-operand': 'off'
    }
  },
  {
    files: ['src/components/FileUpload/index.vue'],
    rules: {
      'no-unused-vars': 'off',
      'vue/multi-word-component-names': 'off',
      'vue/valid-define-emits': 'off'
    }
  },
  {
    files: ['src/views/tool/build/RightPanel.vue'],
    rules: {
      'no-unused-vars': 'off',
      'vue/no-deprecated-slot-attribute': 'off',
      'vue/no-mutating-props': 'off'
    }
  },
  {
    files: ['src/views/tool/gen/genInfoForm.vue'],
    rules: {
      'no-unused-vars': 'off',
      'vue/no-mutating-props': 'off'
    }
  },
  {
    files: [
      'src/components/Breadcrumb/index.vue',
      'src/components/Crontab/result.vue',
      'src/components/HeaderSearch/index.vue',
      'src/components/ImagePreview/index.vue',
      'src/components/ParentView/index.vue',
      'src/components/Screenfull/index.vue',
      'src/components/SizeSelect/index.vue',
      'src/components/SvgIcon/index.vue',
      'src/components/TreePanel/index.vue',
      'src/components/iFrame/index.vue',
      'src/layout/components/Copyright/index.vue',
      'src/layout/components/HeaderNotice/index.vue',
      'src/layout/components/IframeToggle/index.vue',
      'src/layout/components/Navbar.vue',
      'src/layout/components/Settings/index.vue',
      'src/layout/components/Sidebar/Link.vue',
      'src/layout/components/Sidebar/Logo.vue',
      'src/layout/components/Sidebar/index.vue',
      'src/main.js',
      'src/views/error/401.vue',
      'src/views/error/404.vue',
      'src/views/index.vue',
      'src/views/lock.vue',
      'src/views/login.vue',
      'src/views/monitor/cache/index.vue',
      'src/views/monitor/druid/index.vue',
      'src/views/monitor/online/index.vue',
      'src/views/monitor/server/index.vue',
      'src/views/redirect/index.vue',
      'src/views/system/post/index.vue',
      'src/views/system/user/profile/index.vue',
      'src/views/system/user/view.vue',
      'src/views/tool/swagger/index.vue'
    ],
    rules: { 'vue/multi-word-component-names': 'off' }
  },
  {
    files: [
      'src/components/Crontab/day.vue',
      'src/components/Crontab/hour.vue',
      'src/components/Crontab/min.vue',
      'src/components/Crontab/month.vue',
      'src/components/Crontab/second.vue',
      'src/components/Crontab/week.vue',
      'src/components/Crontab/year.vue'
    ],
    rules: {
      'vue/multi-word-component-names': 'off',
      'vue/no-side-effects-in-computed-properties': 'off',
      'vue/require-valid-default-prop': 'off'
    }
  },
  {
    files: [
      'src/components/Hamburger/index.vue',
      'src/components/ImageUpload/index.vue',
      'src/components/Pagination/index.vue'
    ],
    rules: {
      'vue/multi-word-component-names': 'off',
      'vue/valid-define-emits': 'off'
    }
  },
  {
    files: [
      'src/views/system/user/profile/userInfo.vue',
      'src/views/tool/build/DraggableItem.vue',
      'src/views/tool/gen/basicInfoForm.vue'
    ],
    rules: { 'vue/no-mutating-props': 'off' }
  },
  {
    files: ['src/views/tool/build/IconsDialog.vue'],
    rules: { 'vue/no-unused-vars': 'off' }
  },
  // Narrow legacy exceptions; remove each entry when the listed file is migrated.
  {
    files: ['src/components/Crontab/result.vue'],
    rules: { 'no-unused-labels': 'off' }
  },
  {
    files: [
      'src/components/ImageUpload/index.vue',
      'src/layout/components/HeaderNotice/DetailView.vue',
      'src/store/modules/tagsView.js',
      'src/utils/index.js'
    ],
    rules: { 'no-useless-assignment': 'off' }
  },
  {
    files: ['src/components/RightToolbar/index.vue', 'src/directive/common/copyText.js'],
    rules: { 'no-empty': 'off' }
  },
  {
    files: ['src/plugins/download.js'],
    rules: { 'no-redeclare': 'off' }
  },
  {
    files: ['src/store/modules/settings.js'],
    rules: { 'no-prototype-builtins': 'off' }
  },
  {
    files: ['src/utils/validate.js'],
    rules: { 'no-useless-escape': 'off' }
  },
  {
    files: ['src/utils/generator/html.js'],
    linterOptions: { reportUnusedDisableDirectives: false }
  }
]
