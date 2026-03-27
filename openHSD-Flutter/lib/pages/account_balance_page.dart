part of 'chat_page.dart';

class _AccountBalancePage extends StatefulWidget {
  const _AccountBalancePage();

  @override
  State<_AccountBalancePage> createState() => _AccountBalancePageState();
}

class _AccountBalancePageState extends State<_AccountBalancePage> {
  bool _loading = true;
  String? _error;
  double _limit = 0;
  double _usage = 0;
  double _remaining = 0;
  List<Map<String, dynamic>> _orders = const [];

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final auth = context.read<AuthController>();
    final api = ApiClient(token: auth.token);
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final keyInfo = await api.getRechargeKeyInfo();
      final key =
          (keyInfo['keyInfo'] as Map?)?.cast<String, dynamic>() ?? const {};
      final limit =
          (key['limit'] as num?)?.toDouble() ??
          double.tryParse(key['limit']?.toString() ?? '') ??
          0;
      final usage =
          (key['usage'] as num?)?.toDouble() ??
          double.tryParse(key['usage']?.toString() ?? '') ??
          0;
      final remaining =
          (key['limitRemaining'] as num?)?.toDouble() ??
          double.tryParse(key['limitRemaining']?.toString() ?? '') ??
          (limit - usage);
      final orders = await api.getRechargeHistory(limit: 10);
      if (!mounted) return;
      setState(() {
        _limit = limit;
        _usage = usage;
        _remaining = remaining;
        _orders = orders;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString().replaceFirst('Exception: ', '');
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _OhsdChatTheme.pageBg,
      appBar: AppBar(
        backgroundColor: _OhsdChatTheme.bgSurface,
        elevation: 0,
        scrolledUnderElevation: 0,
        surfaceTintColor: Colors.transparent,
        title: const Text(
          '账号余额',
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
          if (_loading)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 20),
              child: Center(child: CircularProgressIndicator()),
            ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text(
                _error!,
                style: const TextStyle(
                  color: _OhsdChatTheme.error,
                  fontSize: 12,
                ),
              ),
            ),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(14),
              gradient: const LinearGradient(
                colors: [Color(0xFF181B22), Color(0xFF2A3040)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x22000000),
                  blurRadius: 10,
                  offset: Offset(0, 4),
                ),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '可用余额',
                  style: TextStyle(
                    color: Color(0xCCFFFFFF),
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  '\$ ${_remaining.toStringAsFixed(2)}',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 34,
                    fontWeight: FontWeight.w700,
                    height: 1,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  '总额度 \$${_limit.toStringAsFixed(2)}    已用 \$${_usage.toStringAsFixed(2)}',
                  style: const TextStyle(
                    color: Color(0xB3FFFFFF),
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _balanceActionButton(
            icon: Icons.add_circle_outline,
            text: '充值',
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute<void>(builder: (_) => const _RechargePage()),
              );
            },
          ),
          const SizedBox(height: 14),
          Container(
            decoration: BoxDecoration(
              color: _OhsdChatTheme.bgSurface,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: _OhsdChatTheme.borderHairline),
            ),
            child: Column(
              children: [
                const Padding(
                  padding: EdgeInsets.fromLTRB(14, 12, 14, 8),
                  child: Row(
                    children: [
                      Text(
                        '收支明细',
                        style: TextStyle(
                          color: _OhsdChatTheme.textPrimary,
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
                const Divider(height: 1, color: _OhsdChatTheme.borderHairline),
                if (_orders.isEmpty)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 16),
                    child: Text(
                      '暂无充值记录',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 12,
                        color: _OhsdChatTheme.textTertiary,
                      ),
                    ),
                  ),
                ..._orders.map(
                  (item) => ListTile(
                    dense: true,
                    visualDensity: const VisualDensity(vertical: -1),
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 2,
                    ),
                    title: Text(
                      '订单 ${item['orderNo'] ?? '--'}',
                      style: const TextStyle(
                        color: _OhsdChatTheme.textPrimary,
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    subtitle: Text(
                      '${item['statusText'] ?? '未知状态'} · ${item['createdAt'] ?? ''}',
                      style: const TextStyle(
                        color: _OhsdChatTheme.textTertiary,
                        fontSize: 12,
                      ),
                    ),
                    trailing: Text(
                      '+ \$${item['amountUsd'] ?? '--'}',
                      style: TextStyle(
                        color: _OhsdChatTheme.success,
                        fontWeight: FontWeight.w600,
                        fontSize: 14,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          TextButton(onPressed: _loadData, child: const Text('刷新余额与订单')),
        ],
      ),
    );
  }

  Widget _balanceActionButton({
    required IconData icon,
    required String text,
    required VoidCallback onTap,
  }) {
    return Material(
      color: _OhsdChatTheme.bgSurface,
      borderRadius: BorderRadius.circular(10),
      child: InkWell(
        borderRadius: BorderRadius.circular(10),
        onTap: onTap,
        child: Container(
          height: 42,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: _OhsdChatTheme.borderHairline),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 18, color: _OhsdChatTheme.primary),
              const SizedBox(width: 6),
              Text(
                text,
                style: const TextStyle(
                  color: _OhsdChatTheme.textPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
