<script>
	export default {
		onLaunch: function() {
			console.log('[openHSD] App Launch')
		},
		onShow: function() {
			console.log('[openHSD] App Show')
		},
		onHide: function() {
			console.log('[openHSD] App Hide')
		}
	}
</script>

<style lang="less">
	@import './styles/theme.less';

	/* ===== 全局重置 ===== */
	page {
		background-color: @ohsd-app-page-bg;
		font-family: -apple-system, 'PingFang SC', 'Helvetica Neue', 'Microsoft YaHei', sans-serif;
		color: @ohsd-text-primary;
		box-sizing: border-box;
	}

	* {
		box-sizing: border-box;
	}

	/* ===== 滚动条隐藏（H5） ===== */
	::-webkit-scrollbar {
		display: none;
	}

	/* ===== 通用动画 ===== */
	@keyframes spin {
		from { transform: rotate(0deg); }
		to   { transform: rotate(360deg); }
	}

	@keyframes float {
		0%, 100% { transform: translateY(0px); }
		50%       { transform: translateY(-10px); }
	}

	@keyframes pulse-dot {
		0%, 100% { box-shadow: 0 0 0 2px rgba(52, 199, 89, 0.2); }
		50%       { box-shadow: 0 0 0 5px rgba(52, 199, 89, 0.08); }
	}

	@keyframes fadeSlideIn {
		from { opacity: 0; transform: translateY(-4px); }
		to   { opacity: 1; transform: translateY(0); }
	}

	/* ===== markdown 样式（注入 rich-text / innerHTML） ===== */
	.markdown-body {
		font-size: 14px;
		line-height: 1.65;
		color: inherit;
		word-break: break-word;
	}

	.markdown-body p { margin: 4px 0 6px; }

	.markdown-body h1 { font-size: 1.4em; font-weight: 700; margin: 14px 0 6px; color: @ohsd-markdown-heading; }
	.markdown-body h2 { font-size: 1.2em; font-weight: 700; margin: 12px 0 5px; color: @ohsd-markdown-heading; }
	.markdown-body h3 { font-size: 1em; font-weight: 700; margin: 10px 0 4px; padding-left: 8px; border-left: 3px solid @ohsd-markdown-h3-border; }

	.markdown-body strong { font-weight: 700; color: @ohsd-markdown-strong; }

	.markdown-body code {
		background: @ohsd-markdown-code-bg;
		padding: 1px 5px;
		border-radius: 3px;
		font-family: 'Courier New', monospace;
		font-size: 0.88em;
		color: @ohsd-markdown-code;
	}

	.markdown-body pre {
		background: @ohsd-markdown-pre-bg;
		padding: 10px 14px;
		border-radius: 6px;
		overflow-x: auto;
		margin: 6px 0;
		border: 1px solid @ohsd-markdown-pre-border;
	}

	.markdown-body pre code {
		background: none;
		padding: 0;
		font-size: 0.85em;
		color: inherit;
	}

	.markdown-body ul, .markdown-body ol {
		padding-left: 1.6em;
		margin: 4px 0 6px;
	}

	.markdown-body li { margin: 3px 0; line-height: 1.6; }

	.markdown-body blockquote {
		border-left: 3px solid @ohsd-markdown-blockquote-border;
		padding: 2px 0 2px 12px;
		margin: 6px 0;
		color: @ohsd-markdown-blockquote-text;
	}

	.markdown-body table {
		border-collapse: collapse;
		width: 100%;
		margin: 8px 0;
		font-size: 0.93em;
	}

	.markdown-body th, .markdown-body td {
		border: 1px solid @ohsd-markdown-table-border;
		padding: 5px 10px;
		text-align: left;
	}

	.markdown-body th { background: @ohsd-markdown-table-head-bg; font-weight: 600; }
	.markdown-body tr:nth-child(even) td { background: @ohsd-markdown-table-row-alt; }

	.markdown-body img { max-width: 100%; border-radius: 4px; }
	.markdown-body hr { border: none; border-top: 1px solid @ohsd-markdown-hr; margin: 8px 0; }
</style>
