<template>
  <div class="app-container">
    <el-card>
      <h2>✅ 我的测试页面</h2>
      <p>路由地址：test-page</p>
      <p>组件路径：kmp/knowledge-group/test</p>
      <p>完整路由地址：/knowlegde_security/test-page</p>
      <p>组件文件：src/views/kmp/knowledge-group/test.vue</p>
      <el-button type="primary">测试按钮</el-button>
    </el-card>
  </div>
</template>

<script setup>
// 最简页面，不需要写任何业务代码
console.log('测试页面加载成功！')
</script>