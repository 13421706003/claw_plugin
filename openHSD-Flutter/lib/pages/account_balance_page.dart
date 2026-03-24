part of 'chat_page.dart';

/// 账号余额（模拟页）
class _AccountBalancePage extends StatelessWidget {
  const _AccountBalancePage();

  @override
  Widget build(BuildContext context) {
    const totalBalance = 128.60;
    const frozenAmount = 20.00;
    const canUse = totalBalance - frozenAmount;
    final records = <({String title, String time, String amount, bool plus})>[
      (
        title: 'API 调用扣费',
        time: '今天 14:22',
        amount: '- ¥3.20',
        plus: false,
      ),
      (
        title: '充值到账',
        time: '昨天 18:06',
        amount: '+ ¥100.00',
        plus: true,
      ),
      (
        title: '语音识别扣费',
        time: '昨天 10:31',
        amount: '- ¥1.80',
        plus: false,
      ),
      (
        title: '系统赠送',
        time: '03-21 09:00',
        amount: '+ ¥30.00',
        plus: true,
      ),
    ];

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
                  '¥ ${canUse.toStringAsFixed(2)}',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 34,
                    fontWeight: FontWeight.w700,
                    height: 1,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  '总余额 ¥${totalBalance.toStringAsFixed(2)}    冻结 ¥${frozenAmount.toStringAsFixed(2)}',
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
                MaterialPageRoute<void>(
                  builder: (_) => const _RechargePage(),
                ),
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
                ...records.map(
                  (item) => ListTile(
                    dense: true,
                    visualDensity: const VisualDensity(vertical: -1),
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 2,
                    ),
                    title: Text(
                      item.title,
                      style: const TextStyle(
                        color: _OhsdChatTheme.textPrimary,
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    subtitle: Text(
                      item.time,
                      style: const TextStyle(
                        color: _OhsdChatTheme.textTertiary,
                        fontSize: 12,
                      ),
                    ),
                    trailing: Text(
                      item.amount,
                      style: TextStyle(
                        color: item.plus
                            ? _OhsdChatTheme.success
                            : _OhsdChatTheme.error,
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
          const Text(
            '此页面为演示 UI，数据均为模拟。',
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
