part of 'chat_page.dart';

class _RechargePage extends StatefulWidget {
  final Future<void> Function()? onBalanceRefresh;

  const _RechargePage({this.onBalanceRefresh});

  @override
  State<_RechargePage> createState() => _RechargePageState();
}

class _RechargePageState extends State<_RechargePage> {
  static const _amounts = [30, 50, 100, 200, 500, 1000];
  int _selectedAmount = 100;
  int _payType = 0; // 0: 微信 1: 支付宝
  bool _submitting = false;
  bool _loadingRate = true;
  double _exchangeRate = 8.0;

  @override
  void initState() {
    super.initState();
    _loadExchangeRate();
  }

  Future<void> _loadExchangeRate() async {
    final auth = context.read<AuthController>();
    final api = ApiClient(token: auth.token);
    try {
      final rate = await api.getRechargeExchangeRate();
      if (!mounted) return;
      setState(() {
        _exchangeRate = rate;
        _loadingRate = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loadingRate = false);
    }
  }

  Future<void> _submitRecharge() async {
    if (_submitting) return;
    final auth = context.read<AuthController>();
    final api = ApiClient(token: auth.token);

    setState(() => _submitting = true);
    try {
      final channel = _payType == 0 ? 'wechat' : 'alipay';
      final amountUsd = _selectedAmount / _exchangeRate;
      final paymentType = _payType == 0 ? 'NATIVE' : 'APP';
      final order = await api.createRechargeOrder(
        amountUsd: amountUsd,
        paymentChannel: channel,
        paymentType: paymentType,
      );
      final orderNo = (order['orderNo'] ?? '').toString();
      final payload = (order['qrcodeUrl'] ?? '').toString();
      final launchUri = _extractPayLaunchUri(payload);
      if (!mounted) return;
      if (orderNo.isEmpty || launchUri == null) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('下单成功但未拿到可拉起的支付链接')));
        return;
      }
      final mode =
          (_payType == 1 &&
              (launchUri.scheme == 'http' || launchUri.scheme == 'https'))
          ? LaunchMode.inAppBrowserView
          : LaunchMode.externalApplication;
      final launched = await launchUrl(launchUri, mode: mode);
      if (!mounted) return;
      if (!launched) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('未能打开支付页面，请稍后重试')));
      }
      await _showPayConfirmDialog(
        api: api,
        orderNo: orderNo,
        channelName: _payType == 0 ? '微信' : '支付宝',
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Uri? _extractPayLaunchUri(String payload) {
    if (payload.isEmpty) return null;
    final direct = Uri.tryParse(payload);
    if (direct != null &&
        (direct.scheme == 'http' ||
            direct.scheme == 'https' ||
            direct.scheme == 'weixin' ||
            direct.scheme == 'alipays')) {
      return direct;
    }
    final actionMatch = RegExp(r'action=\"([^\"]+)\"').firstMatch(payload);
    final actionUrl = actionMatch?.group(1);
    if (actionUrl == null || actionUrl.isEmpty) return null;
    return Uri.tryParse(actionUrl);
  }

  Future<void> _showPayConfirmDialog({
    required ApiClient api,
    required String orderNo,
    required String channelName,
  }) async {
    final paid = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) => AlertDialog(
        title: Text('$channelName支付确认'),
        content: const Text('请确认是否支付成功？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('未支付'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('已支付'),
          ),
        ],
      ),
    );
    if (!mounted) return;
    await _queryOrderAndRefresh(
      api: api,
      orderNo: orderNo,
      paidConfirmed: paid == true,
    );
  }

  Future<void> _queryOrderAndRefresh({
    required ApiClient api,
    required String orderNo,
    required bool paidConfirmed,
  }) async {
    try {
      final status = await api.getRechargeOrderStatus(orderNo);
      final statusText = status['statusText']?.toString() ?? '未知';
      if (!mounted) return;
      final text = paidConfirmed
          ? '已查询订单状态：$statusText'
          : '已收到未支付反馈，订单状态查询结果：$statusText';
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('订单状态查询失败：${e.toString().replaceFirst('Exception: ', '')}')),
      );
    } finally {
      await widget.onBalanceRefresh?.call();
    }
  }

  @override
  Widget build(BuildContext context) {
    final usdPreview = (_selectedAmount / _exchangeRate).toStringAsFixed(2);
    return Scaffold(
      backgroundColor: _OhsdChatTheme.pageBg,
      appBar: AppBar(
        backgroundColor: _OhsdChatTheme.bgSurface,
        elevation: 0,
        scrolledUnderElevation: 0,
        surfaceTintColor: Colors.transparent,
        title: const Text(
          '余额充值',
          style: TextStyle(
            color: _OhsdChatTheme.textPrimary,
            fontSize: 17,
            fontWeight: FontWeight.w600,
          ),
        ),
        iconTheme: const IconThemeData(color: _OhsdChatTheme.textPrimary),
        bottom: const PreferredSize(
          preferredSize: Size.fromHeight(1),
          child: Divider(
            height: 1,
            thickness: 1,
            color: _OhsdChatTheme.borderHairline,
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(14, 14, 14, 20),
        children: [
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: _OhsdChatTheme.bgSurface,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: _OhsdChatTheme.borderHairline),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '选择充值金额',
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: _OhsdChatTheme.textPrimary,
                  ),
                ),
                const SizedBox(height: 12),
                GridView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: _amounts.length,
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    mainAxisSpacing: 10,
                    crossAxisSpacing: 10,
                    childAspectRatio: 3.2,
                  ),
                  itemBuilder: (context, index) {
                    final v = _amounts[index];
                    final selected = _selectedAmount == v;
                    return InkWell(
                      borderRadius: BorderRadius.circular(10),
                      onTap: () => setState(() => _selectedAmount = v),
                      child: Container(
                        decoration: BoxDecoration(
                          color: selected
                              ? _OhsdChatTheme.primary.withValues(alpha: 0.10)
                              : _OhsdChatTheme.bgPage,
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(
                            color: selected
                                ? _OhsdChatTheme.primary
                                : _OhsdChatTheme.borderHairline,
                          ),
                        ),
                        alignment: Alignment.center,
                        child: Text(
                          '¥ $v',
                          style: TextStyle(
                            color: selected
                                ? _OhsdChatTheme.primary
                                : _OhsdChatTheme.textPrimary,
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: _OhsdChatTheme.bgSurface,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: _OhsdChatTheme.borderHairline),
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.currency_exchange,
                  size: 18,
                  color: _OhsdChatTheme.textSecondary,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    _loadingRate
                        ? '正在获取汇率...'
                        : '当前汇率：1 USD = ${_exchangeRate.toStringAsFixed(2)} CNY',
                    style: const TextStyle(
                      fontSize: 13,
                      color: _OhsdChatTheme.textSecondary,
                    ),
                  ),
                ),
                Text(
                  '约 $usdPreview USD',
                  style: const TextStyle(
                    fontSize: 12,
                    color: _OhsdChatTheme.textTertiary,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Container(
            decoration: BoxDecoration(
              color: _OhsdChatTheme.bgSurface,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: _OhsdChatTheme.borderHairline),
            ),
            child: Column(
              children: [
                RadioListTile<int>(
                  value: 0,
                  groupValue: _payType,
                  onChanged: (v) => setState(() => _payType = v ?? 0),
                  activeColor: _OhsdChatTheme.primary,
                  title: const Text(
                    '微信支付',
                    style: TextStyle(color: _OhsdChatTheme.textPrimary),
                  ),
                  secondary: const Icon(Icons.wechat, color: Color(0xFF27C24C)),
                ),
                const Divider(height: 1, color: _OhsdChatTheme.borderHairline),
                RadioListTile<int>(
                  value: 1,
                  groupValue: _payType,
                  onChanged: (v) => setState(() => _payType = v ?? 1),
                  activeColor: _OhsdChatTheme.primary,
                  title: const Text(
                    '支付宝',
                    style: TextStyle(color: _OhsdChatTheme.textPrimary),
                  ),
                  secondary: const Icon(
                    Icons.account_balance_wallet_rounded,
                    color: Color(0xFF2A9DFF),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          Material(
            color: _OhsdChatTheme.primary,
            borderRadius: BorderRadius.circular(12),
            child: InkWell(
              borderRadius: BorderRadius.circular(12),
              onTap: _loadingRate || _submitting ? null : _submitRecharge,
              child: Container(
                height: 46,
                alignment: Alignment.center,
                child: _submitting
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : Text(
                        '立即充值 ¥$_selectedAmount',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
              ),
            ),
          ),
          const SizedBox(height: 10),
          const Text(
            '提交后会直接拉起微信/支付宝 App，请完成支付后返回查看状态。',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 12, color: _OhsdChatTheme.textTertiary),
          ),
        ],
      ),
    );
  }
}

