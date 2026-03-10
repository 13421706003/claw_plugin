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

<style>
	/* ===== 全局重置 ===== */
	page {
		background-color: #ffffff;
		font-family: -apple-system, 'PingFang SC', 'Helvetica Neue', 'Microsoft YaHei', sans-serif;
		color: rgba(0, 0, 0, 0.88);
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

	.markdown-body h1 { font-size: 1.4em; font-weight: 700; margin: 14px 0 6px; color: #1a1a1a; }
	.markdown-body h2 { font-size: 1.2em; font-weight: 700; margin: 12px 0 5px; color: #1a1a1a; }
	.markdown-body h3 { font-size: 1em; font-weight: 700; margin: 10px 0 4px; padding-left: 8px; border-left: 3px solid #bbb; }

	.markdown-body strong { font-weight: 700; color: #111; }

	.markdown-body code {
		background: #f0f0f0;
		padding: 1px 5px;
		border-radius: 3px;
		font-family: 'Courier New', monospace;
		font-size: 0.88em;
		color: #d56161;
	}

	.markdown-body pre {
		background: #f6f8fa;
		padding: 10px 14px;
		border-radius: 6px;
		overflow-x: auto;
		margin: 6px 0;
		border: 1px solid #eaecef;
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
		border-left: 3px solid #d0d0d0;
		padding: 2px 0 2px 12px;
		margin: 6px 0;
		color: #666;
	}

	.markdown-body table {
		border-collapse: collapse;
		width: 100%;
		margin: 8px 0;
		font-size: 0.93em;
	}

	.markdown-body th, .markdown-body td {
		border: 1px solid #e0e0e0;
		padding: 5px 10px;
		text-align: left;
	}

	.markdown-body th { background: #f5f5f5; font-weight: 600; }
	.markdown-body tr:nth-child(even) td { background: #fafafa; }

	.markdown-body img { max-width: 100%; border-radius: 4px; }
	.markdown-body hr { border: none; border-top: 1px solid #ebebeb; margin: 8px 0; }
</style>
