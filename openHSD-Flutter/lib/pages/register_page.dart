import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:provider/provider.dart';

import '../controllers/auth_controller.dart';

class RegisterPage extends StatefulWidget {
  const RegisterPage({super.key});

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  final _usernameCtl = TextEditingController();
  final _passwordCtl = TextEditingController();
  final _confirmCtl = TextEditingController();

  final _usernameFocus = FocusNode();
  final _passwordFocus = FocusNode();
  final _confirmFocus = FocusNode();

  String _focusedField = '';

  bool _loading = false;
  String _errorMsg = '';
  String _successMsg = '';

  String _errUsername = '';
  String _errPassword = '';
  String _errConfirm = '';

  bool _showPassword = false;

  Timer? _timer;

  @override
  void initState() {
    super.initState();

    _usernameFocus.addListener(() {
      if (_usernameFocus.hasFocus) {
        setState(() => _focusedField = 'username');
      } else {
        setState(() => _focusedField = '');
      }
    });
    _passwordFocus.addListener(() {
      if (_passwordFocus.hasFocus) {
        setState(() => _focusedField = 'password');
      } else {
        setState(() => _focusedField = '');
      }
    });
    _confirmFocus.addListener(() {
      if (_confirmFocus.hasFocus) {
        setState(() => _focusedField = 'confirm');
      } else {
        setState(() => _focusedField = '');
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _usernameCtl.dispose();
    _passwordCtl.dispose();
    _confirmCtl.dispose();
    _usernameFocus.dispose();
    _passwordFocus.dispose();
    _confirmFocus.dispose();
    super.dispose();
  }

  double _rpx(BuildContext context, double v) {
    final w = MediaQuery.of(context).size.width;
    return v * w / 750.0;
  }

  Color get _textPrimary => const Color.fromRGBO(0, 0, 0, 0.88);
  Color get _textSecondary => const Color.fromRGBO(0, 0, 0, 0.55);
  Color get _textFaint => const Color.fromRGBO(0, 0, 0, 0.28);
  Color get _borderDefault => const Color.fromRGBO(0, 0, 0, 0.12);
  Color get _borderFocus => const Color.fromRGBO(0, 0, 0, 0.55);
  Color get _borderError => const Color.fromRGBO(255, 77, 79, 0.7);
  Color get _btnBg => const Color.fromRGBO(0, 0, 0, 0.88);
  Color get _btnText => const Color(0xFFFFFFFF);

  Color get _errorStripBg => const Color.fromRGBO(255, 77, 79, 0.07);
  Color get _errorText => const Color(0xFFCF1322);
  Color get _errorDismiss => const Color.fromRGBO(207, 19, 34, 0.5);

  Color get _successStripBg => const Color.fromRGBO(82, 196, 26, 0.08);
  Color get _successText => const Color(0xFF389E0D);

  Color get _textStrong => const Color(0xFF262626);
  Color get _textSubtle => const Color.fromRGBO(0, 0, 0, 0.38);
  Color get _textCaption => const Color.fromRGBO(0, 0, 0, 0.22);
  Color get _iconColor => const Color(0xFF262626);

  bool _validate() {
    final username = _usernameCtl.text.trim();
    final password = _passwordCtl.text;
    final confirm = _confirmCtl.text;

    _errUsername = username.isNotEmpty ? '' : '请输入用户名';
    _errPassword = password.length >= 6 ? '' : '密码至少 6 位字符';
    _errConfirm = confirm == password ? '' : '两次密码不一致';

    setState(() {});
    return _errUsername.isEmpty && _errPassword.isEmpty && _errConfirm.isEmpty;
  }

  Future<void> _onRegister() async {
    if (_loading) return;
    if (!_validate()) return;

    setState(() {
      _loading = true;
      _errorMsg = '';
      _successMsg = '';
    });

    try {
      final auth = context.read<AuthController>();
      await auth.register(
        username: _usernameCtl.text.trim(),
        password: _passwordCtl.text,
      );

      if (!mounted) return;
      setState(() => _successMsg = '注册成功！正在跳转...');

      _timer?.cancel();
      _timer = Timer(const Duration(milliseconds: 1200), () {
        if (!mounted) return;
        Navigator.of(context).pushReplacementNamed('/login');
      });
    } catch (e) {
      _errorMsg = e.toString().replaceFirst('Exception: ', '');
      setState(() {});
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Widget _buildStrip() {
    if (_errorMsg.isNotEmpty) {
      return Container(
        width: double.infinity,
        padding: EdgeInsets.symmetric(
          vertical: _rpx(context, 24),
          horizontal: _rpx(context, 28),
        ),
        margin: EdgeInsets.only(bottom: _rpx(context, 36)),
        decoration: BoxDecoration(
          color: _errorStripBg,
          borderRadius: BorderRadius.circular(_rpx(context, 16)),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                _errorMsg,
                style: TextStyle(
                  fontSize: _rpx(context, 28),
                  color: _errorText,
                  height: 1.45,
                ),
              ),
            ),
            Padding(
              padding: EdgeInsets.all(_rpx(context, 8)),
              child: InkWell(
                onTap: () => setState(() => _errorMsg = ''),
                child: Text(
                  '×',
                  style: TextStyle(
                    fontSize: _rpx(context, 36),
                    color: _errorDismiss,
                  ),
                ),
              ),
            ),
          ],
        ),
      );
    }

    if (_successMsg.isNotEmpty) {
      return Container(
        width: double.infinity,
        padding: EdgeInsets.symmetric(
          vertical: _rpx(context, 24),
          horizontal: _rpx(context, 28),
        ),
        margin: EdgeInsets.only(bottom: _rpx(context, 36)),
        decoration: BoxDecoration(
          color: _successStripBg,
          borderRadius: BorderRadius.circular(_rpx(context, 16)),
        ),
        child: Text(
          _successMsg,
          style: TextStyle(
            fontSize: _rpx(context, 28),
            color: _successText,
            height: 1.45,
          ),
        ),
      );
    }

    return const SizedBox.shrink();
  }

  Widget _buildField({
    required String label,
    required String focusedKey,
    required String errorText,
    required Widget input,
  }) {
    final focused = _focusedField == focusedKey;
    final underlineColor = errorText.isNotEmpty
        ? _borderError
        : focused
        ? _borderFocus
        : _borderDefault;

    return Container(
      margin: EdgeInsets.only(bottom: _rpx(context, 44)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: TextStyle(
              fontSize: _rpx(context, 28),
              color: _textSecondary,
            ),
          ),
          Container(
            padding: EdgeInsets.only(bottom: _rpx(context, 12)),
            decoration: BoxDecoration(
              border: Border(
                bottom: BorderSide(
                  color: underlineColor,
                  width: _rpx(context, 2),
                ),
              ),
            ),
            child: input,
          ),
          if (errorText.isNotEmpty)
            Padding(
              padding: EdgeInsets.only(top: _rpx(context, 16)),
              child: Text(
                errorText,
                style: TextStyle(
                  fontSize: _rpx(context, 24),
                  color: _borderError,
                  height: 1.0,
                ),
              ),
            ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final mq = MediaQuery.of(context);
    final bgHeight = mq.size.height * 0.30;
    final sidePad = _rpx(context, 56);

    return Scaffold(
      body: Container(
        width: double.infinity,
        height: double.infinity,
        decoration: const BoxDecoration(color: Color(0xFFFAFAFA)),
        child: Stack(
          children: [
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              height: bgHeight,
              child: Opacity(
                opacity: 0.58,
                child: SvgPicture.asset(
                  'assets/dotted-map-bg.svg',
                  fit: BoxFit.cover,
                ),
              ),
            ),
            Positioned.fill(
              child: SafeArea(
                top: false,
                bottom: false,
                child: Padding(
                  padding: EdgeInsets.symmetric(horizontal: sidePad),
                  child: Align(
                    alignment: Alignment.center,
                    child: SingleChildScrollView(
                      child: ConstrainedBox(
                        constraints: BoxConstraints(
                          maxWidth: _rpx(context, 620),
                        ),
                        child: Center(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              _buildBrand(),
                              _buildStrip(),
                              _buildField(
                                label: '用户名',
                                focusedKey: 'username',
                                errorText: _errUsername,
                                input: Row(
                                  children: [
                                    SizedBox(
                                      width: _rpx(context, 48),
                                      height: _rpx(context, 48),
                                      child: Icon(
                                        Icons.person_outline,
                                        size: _rpx(context, 26),
                                        color: _iconColor,
                                      ),
                                    ),
                                    SizedBox(width: _rpx(context, 20)),
                                    Expanded(
                                      child: TextField(
                                        focusNode: _usernameFocus,
                                        controller: _usernameCtl,
                                        style: TextStyle(
                                          fontSize: _rpx(context, 32),
                                          color: _textPrimary,
                                        ),
                                        decoration: InputDecoration(
                                          isDense: true,
                                          border: InputBorder.none,
                                          hintText: '请输入用户名',
                                          hintStyle: TextStyle(
                                            fontSize: _rpx(context, 32),
                                            color: _textFaint,
                                          ),
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              _buildField(
                                label: '密码',
                                focusedKey: 'password',
                                errorText: _errPassword,
                                input: Row(
                                  children: [
                                    SizedBox(
                                      width: _rpx(context, 48),
                                      height: _rpx(context, 48),
                                      child: Icon(
                                        Icons.lock_outline,
                                        size: _rpx(context, 26),
                                        color: _iconColor,
                                      ),
                                    ),
                                    SizedBox(width: _rpx(context, 20)),
                                    Expanded(
                                      child: TextField(
                                        focusNode: _passwordFocus,
                                        controller: _passwordCtl,
                                        obscureText: !_showPassword,
                                        style: TextStyle(
                                          fontSize: _rpx(context, 32),
                                          color: _textPrimary,
                                        ),
                                        decoration: InputDecoration(
                                          isDense: true,
                                          border: InputBorder.none,
                                          hintText: '至少 6 位',
                                          hintStyle: TextStyle(
                                            fontSize: _rpx(context, 32),
                                            color: _textFaint,
                                          ),
                                        ),
                                      ),
                                    ),
                                    SizedBox(width: _rpx(context, 8)),
                                    IconButton(
                                      padding: EdgeInsets.zero,
                                      constraints: const BoxConstraints(),
                                      icon: Icon(
                                        _showPassword
                                            ? Icons.visibility_off_outlined
                                            : Icons.visibility_outlined,
                                        size: _rpx(context, 26),
                                        color: _iconColor,
                                      ),
                                      onPressed: () {
                                        setState(
                                          () => _showPassword = !_showPassword,
                                        );
                                      },
                                    ),
                                  ],
                                ),
                              ),
                              _buildField(
                                label: '确认密码',
                                focusedKey: 'confirm',
                                errorText: _errConfirm,
                                input: Row(
                                  children: [
                                    SizedBox(
                                      width: _rpx(context, 48),
                                      height: _rpx(context, 48),
                                      child: Icon(
                                        Icons.lock_outline,
                                        size: _rpx(context, 26),
                                        color: _iconColor,
                                      ),
                                    ),
                                    SizedBox(width: _rpx(context, 20)),
                                    Expanded(
                                      child: TextField(
                                        focusNode: _confirmFocus,
                                        controller: _confirmCtl,
                                        obscureText: !_showPassword,
                                        style: TextStyle(
                                          fontSize: _rpx(context, 32),
                                          color: _textPrimary,
                                        ),
                                        decoration: InputDecoration(
                                          isDense: true,
                                          border: InputBorder.none,
                                          hintText: '再次输入密码',
                                          hintStyle: TextStyle(
                                            fontSize: _rpx(context, 32),
                                            color: _textFaint,
                                          ),
                                        ),
                                      ),
                                    ),
                                    SizedBox(width: _rpx(context, 8)),
                                    IconButton(
                                      padding: EdgeInsets.zero,
                                      constraints: const BoxConstraints(),
                                      icon: Icon(
                                        _showPassword
                                            ? Icons.visibility_off_outlined
                                            : Icons.visibility_outlined,
                                        size: _rpx(context, 26),
                                        color: _iconColor,
                                      ),
                                      onPressed: () {
                                        setState(
                                          () => _showPassword = !_showPassword,
                                        );
                                      },
                                    ),
                                  ],
                                ),
                              ),
                              _buildRegisterButton(),
                              _buildFoot(),
                              SizedBox(height: _rpx(context, 52)),
                              Text(
                                '会宝 · 2026',
                                style: TextStyle(
                                  fontSize: _rpx(context, 22),
                                  color: _textCaption,
                                ),
                              ),
                              SizedBox(height: _rpx(context, 8)),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBrand() {
    final ringSize = _rpx(context, 200);
    final imgSize = _rpx(context, 112);

    return Padding(
      padding: EdgeInsets.only(bottom: _rpx(context, 56)),
      child: Column(
        children: [
          Container(
            width: ringSize,
            height: ringSize,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(_rpx(context, 48)),
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xFFF2F2F2), Color(0xFFF8F8F8)],
              ),
              boxShadow: [
                BoxShadow(
                  color: const Color.fromRGBO(0, 0, 0, 0.08),
                  blurRadius: _rpx(context, 40),
                  offset: Offset(0, _rpx(context, 12)),
                ),
              ],
            ),
            child: Center(
              child: SvgPicture.asset(
                'assets/logo.svg',
                width: imgSize,
                height: imgSize,
                fit: BoxFit.contain,
              ),
            ),
          ),
          SizedBox(height: _rpx(context, 32)),
          Text(
            '会宝',
            style: TextStyle(
              fontSize: _rpx(context, 40),
              fontWeight: FontWeight.w700,
              color: _textStrong,
              letterSpacing: _rpx(context, 4),
              height: 1.25,
            ),
          ),
          SizedBox(height: _rpx(context, 12)),
          Text(
            '网关对话',
            style: TextStyle(
              fontSize: _rpx(context, 26),
              color: const Color.fromRGBO(0, 0, 0, 0.42),
              height: 1.35,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRegisterButton() {
    return SizedBox(
      height: _rpx(context, 100),
      width: double.infinity,
      child: Opacity(
        opacity: _loading ? 0.75 : 1.0,
        child: ElevatedButton(
          style: ElevatedButton.styleFrom(
            backgroundColor: _btnBg,
            foregroundColor: _btnText,
            elevation: 0,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(_rpx(context, 16)),
            ),
          ),
          onPressed: _loading ? null : _onRegister,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (_loading)
                SizedBox(
                  width: _rpx(context, 32),
                  height: _rpx(context, 32),
                  child: const CircularProgressIndicator(
                    strokeWidth: 3,
                    valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                  ),
                ),
              if (_loading) SizedBox(width: _rpx(context, 14)),
              Text(
                _loading ? '注册中…' : '注册',
                style: TextStyle(
                  fontSize: _rpx(context, 32),
                  fontWeight: FontWeight.w600,
                  color: _btnText,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildFoot() {
    return Padding(
      padding: EdgeInsets.only(top: _rpx(context, 48)),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            '已有账号？',
            style: TextStyle(fontSize: _rpx(context, 28), color: _textSubtle),
          ),
          SizedBox(width: _rpx(context, 10)),
          GestureDetector(
            onTap: () => Navigator.of(context).pushNamed('/login'),
            child: Text(
              '登录',
              style: TextStyle(
                fontSize: _rpx(context, 28),
                fontWeight: FontWeight.w600,
                color: _textStrong,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
