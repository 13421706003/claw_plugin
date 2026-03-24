part of 'chat_page.dart';

class _RechargePage extends StatefulWidget {
  const _RechargePage();

  @override
  State<_RechargePage> createState() => _RechargePageState();
}

class _RechargePageState extends State<_RechargePage> {
  static const _amounts = [30, 50, 100, 200, 500, 1000];
  int _selectedAmount = 100;
  int _payType = 0; // 0: 微信 1: 支付宝

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
                  secondary: const Icon(
                    Icons.wechat,
                    color: Color(0xFF27C24C),
                  ),
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
              onTap: () {
                final payName = _payType == 0 ? '微信支付' : '支付宝';
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text('模拟提交：$payName 充值 ¥$_selectedAmount'),
                  ),
                );
              },
              child: Container(
                height: 46,
                alignment: Alignment.center,
                child: Text(
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
            '温馨提示：充值页为演示 UI，未接入真实支付。',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 12,
              color: _OhsdChatTheme.textTertiary,
            ),
          ),
        ],
      ),
    );
  }
}
