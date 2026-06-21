<template>
  <div class="app-container verification-page">
    <header class="page-header">
      <div><h2>链下验证</h2><p>运行验证并查看生成的证明值</p></div>
      <el-tag :type="online ? 'success' : 'danger'" effect="plain">{{ online ? '验证服务正常' : '验证服务不可用' }}</el-tag>
    </header>
    <el-tabs v-model="active" type="border-card" @tab-click="handleTabClick">
      <el-tab-pane label="Merkle Root生成" name="merkle" />
      <el-tab-pane label="零知识证明ZKP" name="zkp" />
      <el-tab-pane label="门限阈值签名" name="threshold" />
      <el-row :gutter="18">
        <el-col :xs="24" :lg="10">
          <section class="form-panel">
            <h3>{{ titles[active] }}</h3>
            <el-form ref="form" :model="form" :rules="rules" label-position="top">
              <el-form-item label="业务标识" prop="businessId"><el-input v-model.trim="form.businessId" placeholder="例如：traffic-test-001" /></el-form-item>
              <template v-if="active === 'merkle'">
                <el-form-item label="数据来源">
                  <el-radio-group v-model="form.inputMode" size="small">
                    <el-radio-button label="manual">手动输入</el-radio-button>
                    <el-radio-button label="file">选择文件</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item v-if="form.inputMode === 'manual'" label="数据块（每行一个叶子节点）">
                  <el-input v-model="form.dataText" type="textarea" :rows="6" placeholder="speed=40&#10;speed=35&#10;speed=42" />
                </el-form-item>
                <el-form-item v-else label="交通数据文件">
                  <el-upload ref="fileUpload" action="#" :auto-upload="false" :limit="1" :file-list="fileList" :on-change="handleFileChange" :on-remove="handleFileRemove" :on-exceed="handleFileExceed" accept=".txt,.csv,.json,.xml,.log,.md,.pdf,.zip,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg,.bin">
                    <el-button icon="el-icon-folder-opened">选择文件</el-button>
                  </el-upload>
                  <div class="hint file-hint">文本文件按非空行分块；其他文件按64 KiB分块并Base64编码；最大10 MiB。</div>
                  <el-alert v-if="fileInfo" :title="fileInfo" type="info" :closable="false" show-icon />
                </el-form-item>
                <el-form-item label="抽样数据索引">
                  <el-input-number v-model="form.sampleIndex" :min="0" :max="maxIndex" controls-position="right" />
                  <span class="hint">当前范围 0 - {{ maxIndex }}</span>
                </el-form-item>
              </template>
              <template v-else>
                <el-form-item label="Merkle Root">
                  <el-select v-model.trim="form.merkleRoot" filterable allow-create default-first-option placeholder="选择已有Root或粘贴64位十六进制Root" style="width: 100%">
                    <el-option v-for="item in rootOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </template>
              <template v-if="active === 'threshold'">
                <el-row :gutter="12">
                  <el-col :span="12"><el-form-item label="总节点数"><el-input-number v-model="form.totalNodes" :min="1" :max="100" controls-position="right" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="签名阈值"><el-input-number v-model="form.threshold" :min="1" :max="form.totalNodes" controls-position="right" /></el-form-item></el-col>
                </el-row>
                <el-form-item label="参与节点编号" prop="participantText"><el-input v-model.trim="form.participantText" placeholder="用英文逗号分隔，例如：1,2,3" /></el-form-item>
              </template>
              <el-form-item><el-checkbox v-model="form.writeOnChain">将验证结果写入区块链</el-checkbox></el-form-item>
              <el-form-item v-if="form.writeOnChain" label="目标资源">
                <el-select v-model="form.targetChains" multiple filterable clearable :loading="resourceLoading" placeholder="请选择已注册的合约或chaincode资源" style="width: 100%" @visible-change="handleResourceDropdown">
                  <el-option v-for="resource in resourceOptions" :key="resource.path" :label="resource.path" :value="resource.path">
                    <span>{{ resource.path }}</span>
                    <span v-if="resource.stubType" class="resource-type">{{ resource.stubType }}</span>
                  </el-option>
                </el-select>
                <div class="hint resource-hint">来源于WeCross资源管理；所选资源必须实现 saveRecord 方法。</div>
              </el-form-item>
              <el-button type="primary" icon="el-icon-video-play" :loading="loading" @click="run">运行{{ titles[active] }}</el-button>
              <el-button icon="el-icon-refresh-left" @click="reset">重置</el-button>
            </el-form>
          </section>
        </el-col>
        <el-col :xs="24" :lg="14">
          <section class="result-panel">
            <div class="section-title"><h3>验证结果</h3><el-tag v-if="result" :type="result.passed ? 'success' : 'danger'">{{ result.passed ? '验证通过' : '验证失败' }}</el-tag></div>
            <el-empty v-if="!result" description="运行验证后在此查看生成值" :image-size="88" />
            <template v-else>
              <dl class="result-meta">
                <div><dt>验证类型</dt><dd>{{ typeName(result.verifyType) }}</dd></div>
                <div><dt>算法</dt><dd>{{ result.algorithm }}</dd></div>
                <div><dt>业务标识</dt><dd>{{ result.businessId }}</dd></div>
                <div><dt>生成时间</dt><dd>{{ formatTime(result.timestamp) }}</dd></div>
                <div class="wide"><dt>记录 ID</dt><dd><code>{{ result.recordId }}</code></dd></div>
              </dl>
              <div class="values">
                <div v-for="item in values" :key="item.label" class="value-row">
                  <span>{{ item.label }}</span><code>{{ item.value }}</code>
                  <el-button type="text" icon="el-icon-document-copy" title="复制" @click="copy(item.value)" />
                </div>
              </div>
              <el-collapse><el-collapse-item title="查看完整验证详情"><pre>{{ prettyResult }}</pre></el-collapse-item></el-collapse>
            </template>
          </section>
          <section v-if="history.length" class="history-panel">
            <div class="section-title"><h3>最近生成记录</h3><el-button type="text" @click="clearHistory">清空</el-button></div>
            <el-table :data="history" size="small" max-height="220" @row-click="selectRecord">
              <el-table-column prop="businessId" label="业务标识" min-width="130" show-overflow-tooltip />
              <el-table-column label="类型" min-width="120"><template slot-scope="scope">{{ typeName(scope.row.verifyType) }}</template></el-table-column>
              <el-table-column label="结果" width="80"><template slot-scope="scope"><el-tag :type="scope.row.passed ? 'success' : 'danger'" size="mini">{{ scope.row.passed ? '通过' : '失败' }}</el-tag></template></el-table-column>
              <el-table-column label="时间" width="155"><template slot-scope="scope">{{ formatTime(scope.row.timestamp) }}</template></el-table-column>
            </el-table>
          </section>
        </el-col>
      </el-row>
    </el-tabs>
  </div>
</template>

<script>
import { getVerificationHealth, verifyMerkle, verifyGroth16, verifyThresholdSignature } from '@/api/verification'
import { getResourceList } from '@/api/resource'

const HISTORY_KEY = 'wecross-verification-history'
const TYPE_BY_TAB = { merkle: 'MERKLE_ROOT', zkp: 'GROTH16_ZKP', threshold: 'THRESHOLD_SIGNATURE' }
const MAX_FILE_SIZE = 10 * 1024 * 1024
const BINARY_CHUNK_SIZE = 64 * 1024
const TEXT_FILE_PATTERN = /\.(txt|csv|json|xml|log|md)$/i

export default {
  name: 'VerificationWorkbench',
  data() {
    const participants = (rule, value, callback) => {
      if (this.active !== 'threshold') return callback()
      const ids = this.participantIds(value)
      if (!ids.length) return callback(new Error('请输入参与节点编号'))
      if (ids.some(id => id < 1 || id > this.form.totalNodes)) return callback(new Error(`节点编号必须在 1 - ${this.form.totalNodes} 之间`))
      if (new Set(ids).size < this.form.threshold) return callback(new Error(`至少需要 ${this.form.threshold} 个不同参与节点`))
      callback()
    }
    return {
      active: 'merkle', online: false, loading: false, resourceLoading: false, result: null, history: [], resourceOptions: [], fileList: [], fileBlocks: [], fileInfo: '',
      titles: { merkle: 'Merkle Root生成', zkp: '零知识证明ZKP', threshold: '门限阈值签名' },
      form: this.newForm(),
      rules: {
        businessId: [{ required: true, message: '请输入业务标识', trigger: 'blur' }],
        dataText: [{ required: true, message: '请输入至少一个数据块', trigger: 'blur' }],
        participantText: [{ validator: participants, trigger: 'blur' }]
      }
    }
  },
  computed: {
    blocks() {
      if (this.form.inputMode === 'file') return this.fileBlocks
      return this.form.dataText.split(/\r?\n/).map(v => v.trim()).filter(Boolean)
    },
    maxIndex() { return Math.max(0, this.blocks.length - 1) },
    rootOptions() {
      const seen = new Set()
      return this.history.reduce((options, record) => {
        const root = record.detail && record.detail.merkleRoot
        if (root && !seen.has(root)) {
          seen.add(root)
          options.push({ value: root, label: `${record.businessId} - ${root.slice(0, 16)}...` })
        }
        return options
      }, [])
    },
    prettyResult() { return JSON.stringify(this.result, null, 2) },
    values() {
      if (!this.result) return []
      const d = this.result.detail || {}
      const list = [{ label: 'Merkle Root', value: d.merkleRoot }, { label: '数据哈希', value: this.result.dataHash }, { label: '结果哈希', value: this.result.resultHash }, { label: '详情哈希', value: this.result.detailHash }]
      if (this.result.verifyType === 'GROTH16_ZKP') list.splice(1, 0, { label: '证明哈希', value: d.proofHash })
      if (this.result.verifyType === 'THRESHOLD_SIGNATURE') list.splice(1, 0, { label: '签名哈希', value: d.signatureHash }, { label: '聚合签名', value: d.signature })
      return list.filter(v => v.value)
    }
  },
  created() {
    this.loadHistory()
    getVerificationHealth().then(() => { this.online = true }).catch(() => { this.online = false })
  },
  methods: {
    newForm() { return { businessId: `traffic-${Date.now()}`, inputMode: 'manual', dataText: 'speed=40\nspeed=35\nspeed=42', sampleIndex: 1, merkleRoot: '', totalNodes: 5, threshold: 3, participantText: '1,2,3', writeOnChain: false, targetChains: [] } },
    handleFileChange(file, fileList) {
      if (!file.raw) return
      if (file.raw.size > MAX_FILE_SIZE) {
        this.$message.error('文件不能超过10 MiB')
        this.fileList = []
        this.fileBlocks = []
        this.fileInfo = ''
        return
      }
      this.fileList = fileList.slice(-1)
      const reader = new FileReader()
      reader.onerror = () => this.$message.error('文件读取失败')
      reader.onload = event => {
        if (TEXT_FILE_PATTERN.test(file.name)) {
          this.fileBlocks = String(event.target.result).split(/\r?\n/).map(value => value.trim()).filter(Boolean)
        } else {
          const bytes = new Uint8Array(event.target.result)
          this.fileBlocks = []
          for (let offset = 0; offset < bytes.length; offset += BINARY_CHUNK_SIZE) {
            this.fileBlocks.push(this.bytesToBase64(bytes.subarray(offset, offset + BINARY_CHUNK_SIZE)))
          }
        }
        if (!this.fileBlocks.length) {
          this.$message.warning('文件中没有可用数据')
          return
        }
        this.form.sampleIndex = Math.min(this.form.sampleIndex, this.fileBlocks.length - 1)
        this.fileInfo = `${file.name}，${this.fileBlocks.length}个数据块，${this.formatBytes(file.raw.size)}`
      }
      if (TEXT_FILE_PATTERN.test(file.name)) reader.readAsText(file.raw, 'UTF-8')
      else reader.readAsArrayBuffer(file.raw)
    },
    handleFileRemove() {
      this.fileList = []
      this.fileBlocks = []
      this.fileInfo = ''
    },
    handleFileExceed() {
      this.$message.warning('一次只能选择一个文件，请先移除当前文件')
    },
    bytesToBase64(bytes) {
      let binary = ''
      for (let offset = 0; offset < bytes.length; offset += 8192) {
        binary += String.fromCharCode.apply(null, bytes.subarray(offset, offset + 8192))
      }
      return btoa(binary)
    },
    formatBytes(size) {
      if (size < 1024) return `${size} B`
      if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KiB`
      return `${(size / 1024 / 1024).toFixed(1)} MiB`
    },
    async loadResources() {
      this.resourceLoading = true
      try {
        const response = await getResourceList({ path: null, offset: 0, size: 1000 }, null)
        if (response.errorCode !== 0) throw new Error(response.message || '查询资源列表失败')
        this.resourceOptions = (response.data.resourceDetails || []).filter(resource => resource.path)
      } catch (error) {
        this.resourceOptions = []
        this.$message.warning('未能加载WeCross资源列表，请先在资源管理中注册合约或chaincode')
      } finally {
        this.resourceLoading = false
      }
    },
    handleResourceDropdown(visible) {
      if (visible && !this.resourceOptions.length && !this.resourceLoading) this.loadResources()
    },
    run() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        if (this.active === 'merkle' && !this.blocks.length) return this.$message.warning('请输入数据块或选择文件')
        if (this.active === 'merkle' && this.form.sampleIndex > this.maxIndex) return this.$message.warning('抽样索引不能超过数据块范围')
        if (this.active !== 'merkle' && !/^[0-9a-fA-F]{64}$/.test(this.form.merkleRoot)) return this.$message.warning('请选择或输入64位十六进制Merkle Root')
        if (this.form.writeOnChain && !this.form.targetChains.length) return this.$message.warning('请选择至少一个目标资源')
        const payload = { businessId: this.form.businessId, writeOnChain: this.form.writeOnChain, targetChains: this.form.targetChains }
        if (this.active === 'merkle') Object.assign(payload, { dataBlocks: this.blocks, sampleIndex: this.form.sampleIndex })
        else payload.merkleRoot = this.form.merkleRoot
        if (this.active === 'threshold') Object.assign(payload, { totalNodes: this.form.totalNodes, threshold: this.form.threshold, participantIds: this.participantIds(this.form.participantText) })
        const api = { merkle: verifyMerkle, zkp: verifyGroth16, threshold: verifyThresholdSignature }[this.active]
        this.loading = true
        try {
          this.result = await api(payload)
          this.addHistory(this.result)
          this.$message.success('链下验证运行完成')
        } catch (error) {
          const data = error.response && error.response.data
          this.$message.error((data && (data.message || data.error)) || '链下验证运行失败')
        } finally { this.loading = false }
      })
    },
    participantIds(value) { return String(value || '').split(',').map(v => Number(v.trim())).filter(Number.isInteger) },
    reset() { this.form = this.newForm(); this.result = null; this.fileList = []; this.fileBlocks = []; this.fileInfo = ''; this.$nextTick(() => this.$refs.form.clearValidate()) },
    typeName(type) { return { MERKLE_ROOT: 'Merkle Root', GROTH16_ZKP: '零知识证明 ZKP', THRESHOLD_SIGNATURE: '门限阈值签名' }[type] || type },
    formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' },
    copy(value) { navigator.clipboard.writeText(String(value)).then(() => this.$message.success('已复制')).catch(() => this.$message.warning('请手动选择文本复制')) },
    addHistory(record) { this.history = [record, ...this.history.filter(v => v.recordId !== record.recordId)].slice(0, 20); localStorage.setItem(HISTORY_KEY, JSON.stringify(this.history)) },
    loadHistory() {
      try {
        this.history = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]')
      } catch (error) {
        this.history = []
      }
      this.showLatest()
    },
    showLatest() { this.result = this.history.find(v => v.verifyType === TYPE_BY_TAB[this.active]) || null },
    handleTabClick() {
      this.showLatest()
      if (this.active !== 'merkle' && !this.form.merkleRoot && this.rootOptions.length) this.form.merkleRoot = this.rootOptions[0].value
    },
    selectRecord(row) { this.result = row; this.active = Object.keys(TYPE_BY_TAB).find(key => TYPE_BY_TAB[key] === row.verifyType) || this.active },
    clearHistory() { this.history = []; this.result = null; localStorage.removeItem(HISTORY_KEY) }
  }
}
</script>

<style lang="scss" scoped>
.page-header, .section-title { display: flex; align-items: center; justify-content: space-between; }
.page-header { margin-bottom: 16px; }
.page-header h2, h3 { margin: 0; letter-spacing: 0; }
.page-header h2 { font-size: 22px; }
.page-header p { margin: 6px 0 0; color: #909399; font-size: 14px; }
.form-panel, .result-panel, .history-panel { padding: 4px 2px; }
.form-panel h3, .section-title { margin-bottom: 18px; }
.result-panel, .history-panel { padding-left: 18px; border-left: 1px solid #ebeef5; }
.history-panel { margin-top: 22px; }
.hint { margin-left: 10px; color: #909399; font-size: 13px; }
.resource-hint { margin: 6px 0 0; line-height: 20px; }
.resource-type { float: right; margin-left: 16px; color: #909399; font-size: 12px; }
.result-meta { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 0; border: 1px solid #ebeef5; border-bottom: 0; }
.result-meta div { display: grid; grid-template-columns: 82px minmax(0, 1fr); min-height: 38px; border-bottom: 1px solid #ebeef5; }
.result-meta div:nth-child(odd):not(.wide) { border-right: 1px solid #ebeef5; }
.result-meta .wide { grid-column: 1 / -1; }
.result-meta dt, .result-meta dd { display: flex; align-items: center; margin: 0; padding: 8px 10px; overflow-wrap: anywhere; }
.result-meta dt { background: #f5f7fa; color: #909399; font-size: 13px; }
.result-meta dd { color: #303133; font-size: 13px; }.values { margin: 16px 0; border-top: 1px solid #ebeef5; }
.value-row { display: grid; grid-template-columns: 100px minmax(0, 1fr) 34px; align-items: center; min-height: 46px; border-bottom: 1px solid #ebeef5; }
.value-row span { color: #606266; font-size: 13px; }
code { overflow-wrap: anywhere; color: #1f5d8f; font: 12px Consolas, monospace; }
pre { max-height: 310px; margin: 0; padding: 14px; overflow: auto; background: #f5f7fa; border: 1px solid #e4e7ed; border-radius: 4px; font: 12px/1.6 Consolas, monospace; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 1199px) { .result-panel, .history-panel { margin-top: 24px; padding-left: 0; border-left: 0; } }
</style>

