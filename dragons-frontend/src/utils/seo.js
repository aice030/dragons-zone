/**
 * SEO 工具函数
 * 用于动态设置页面标题和 meta 标签
 */

/**
 * 设置页面标题
 * @param {string} title - 页面标题
 */
export function setPageTitle(title) {
  document.title = title
}

/**
 * 设置或更新 meta 标签
 * @param {string} name - meta 标签的 name 或 property
 * @param {string} content - meta 标签的 content
 * @param {string} type - 类型：'name' 或 'property'，默认为 'name'
 */
export function setMetaTag(name, content, type = 'name') {
  if (!content) return

  // 查找已存在的 meta 标签
  const selector = type === 'property' 
    ? `meta[property="${name}"]` 
    : `meta[name="${name}"]`
  
  let metaTag = document.querySelector(selector)
  
  if (metaTag) {
    // 如果存在，更新 content
    metaTag.setAttribute('content', content)
  } else {
    // 如果不存在，创建新的 meta 标签
    metaTag = document.createElement('meta')
    if (type === 'property') {
      metaTag.setAttribute('property', name)
    } else {
      metaTag.setAttribute('name', name)
    }
    metaTag.setAttribute('content', content)
    document.head.appendChild(metaTag)
  }
}

/**
 * 设置页面 SEO 信息
 * @param {Object} options - SEO 配置对象
 * @param {string} options.title - 页面标题
 * @param {string} options.description - 页面描述
 * @param {string} options.keywords - 页面关键词（可选）
 * @param {string} options.image - 页面图片 URL（可选，用于 Open Graph）
 */
export function setSEO({ title, description, keywords, image }) {
  // 设置标题
  if (title) {
    setPageTitle(title)
  }

  // 设置描述
  if (description) {
    setMetaTag('description', description)
    // Open Graph 描述
    setMetaTag('og:description', description, 'property')
  }

  // 设置关键词
  if (keywords) {
    setMetaTag('keywords', keywords)
  }

  // 设置图片（Open Graph）
  if (image) {
    setMetaTag('og:image', image, 'property')
  }

  // 设置 Open Graph 标题
  if (title) {
    setMetaTag('og:title', title, 'property')
  }

  // 设置 Open Graph 类型
  setMetaTag('og:type', 'website', 'property')
}

/**
 * 设置结构化数据（JSON-LD）
 * @param {Object} data - 结构化数据对象
 */
export function setStructuredData(data) {
  // 移除已存在的结构化数据脚本
  const existingScript = document.querySelector('script[type="application/ld+json"]')
  if (existingScript) {
    existingScript.remove()
  }

  // 创建新的结构化数据脚本
  const script = document.createElement('script')
  script.type = 'application/ld+json'
  script.textContent = JSON.stringify(data)
  document.head.appendChild(script)
}

/**
 * 清除所有动态设置的 meta 标签（可选，用于页面切换时清理）
 */
export function clearDynamicMetaTags() {
  // 注意：这个方法只清除我们动态添加的标签，不会清除 HTML 中静态的 meta 标签
  // 如果需要完全清理，需要更复杂的逻辑
  const structuredDataScript = document.querySelector('script[type="application/ld+json"]')
  if (structuredDataScript) {
    structuredDataScript.remove()
  }
}
