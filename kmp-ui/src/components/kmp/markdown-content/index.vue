<template>
  <div class="markdown-body" v-html="renderedHtml"></div>
</template>

<script setup name="MarkdownContent">
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({
  content: { type: String, default: '' }
})

let linkHookRegistered = false

/** 只注册一次链接增强钩子，外部链接统一新窗口打开并阻断反向引用。 */
function ensureLinkHook() {
  if (linkHookRegistered) return
  DOMPurify.addHook('afterSanitizeAttributes', node => {
    if (node.tagName === 'A') {
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', 'noopener noreferrer nofollow')
    }
  })
  linkHookRegistered = true
}

/** 先按 GFM 渲染，再做白名单净化，防止模型输出携带脚本或内联样式。 */
const renderedHtml = computed(() => {
  const rawText = String(props.content || '')
  if (!rawText.trim()) return ''
  ensureLinkHook()
  const rawHtml = marked.parse(rawText, { gfm: true, breaks: true, async: false })
  return DOMPurify.sanitize(rawHtml, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['target', 'rel'],
    FORBID_TAGS: ['style', 'form', 'input', 'button', 'iframe', 'script'],
    FORBID_ATTR: ['style']
  })
})
</script>

<style scoped lang="scss">
.markdown-body {
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 1.8;
  overflow-wrap: anywhere;

  :deep(> *:first-child) { margin-top: 0; }
  :deep(> *:last-child) { margin-bottom: 0; }

  :deep(p) { margin: 0 0 10px; }

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    margin: 18px 0 10px;
    color: var(--el-text-color-primary);
    font-weight: 600;
    line-height: 1.45;
  }

  :deep(h1) { font-size: 20px; }
  :deep(h2) { font-size: 18px; }
  :deep(h3) { font-size: 16px; }
  :deep(h4),
  :deep(h5),
  :deep(h6) { font-size: 14px; }

  :deep(ul),
  :deep(ol) {
    margin: 0 0 10px;
    padding-left: 22px;
  }

  :deep(li) { margin: 3px 0; }
  :deep(li > p) { margin: 0 0 4px; }
  :deep(ul ul),
  :deep(ol ol),
  :deep(ul ol),
  :deep(ol ul) { margin: 4px 0; }

  :deep(strong) { font-weight: 600; }
  :deep(del) { color: var(--el-text-color-secondary); }

  :deep(a) {
    color: var(--el-color-primary);
    text-decoration: none;
    word-break: break-all;

    &:hover { text-decoration: underline; }
  }

  :deep(hr) {
    height: 0;
    margin: 16px 0;
    border: 0;
    border-top: 1px solid var(--el-border-color-lighter);
  }

  :deep(blockquote) {
    margin: 10px 0;
    padding: 6px 12px;
    border-left: 3px solid var(--el-color-primary-light-5);
    background: var(--el-fill-color-lighter);
    color: var(--el-text-color-regular);

    p:last-child { margin-bottom: 0; }
  }

  :deep(code) {
    padding: 1px 5px;
    border-radius: 4px;
    background: var(--el-fill-color);
    color: var(--el-color-danger);
    font-family: Consolas, 'JetBrains Mono', Menlo, Monaco, monospace;
    font-size: 0.9em;
    overflow-wrap: anywhere;
  }

  :deep(pre) {
    margin: 12px 0;
    border-radius: 6px;
    background: #1f2430;
    overflow: hidden;

    code {
      display: block;
      padding: 13px 15px;
      background: transparent;
      color: #e6e8ea;
      font-size: 13px;
      line-height: 1.65;
      white-space: pre;
      overflow-x: auto;
      overflow-wrap: normal;
    }
  }

  :deep(table) {
    display: block;
    width: 100%;
    margin: 12px 0;
    border-collapse: collapse;
    overflow-x: auto;
    font-size: 13px;
  }

  :deep(th),
  :deep(td) {
    padding: 7px 10px;
    border: 1px solid var(--el-border-color-light);
    text-align: left;
  }

  :deep(th) {
    background: var(--el-fill-color-light);
    font-weight: 600;
  }

  :deep(img) {
    max-width: 100%;
    border-radius: 6px;
  }
}
</style>
