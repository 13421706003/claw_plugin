import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:provider/provider.dart';

import '../controllers/auth_controller.dart';

class SplashPage extends StatefulWidget {
  const SplashPage({super.key});

  @override
  State<SplashPage> createState() => _SplashPageState();
}

class _SplashPageState extends State<SplashPage> {
  @override
  void initState() {
    super.initState();

    Future.delayed(const Duration(milliseconds: 600), () {
      final auth = context.read<AuthController>();
      final target = auth.isLoggedIn ? '/chat' : '/login';
      Navigator.of(context).pushReplacementNamed(target);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          Positioned.fill(
            child: SvgPicture.asset(
              'assets/dotted-map-bg.svg',
              fit: BoxFit.cover,
            ),
          ),
          Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const SizedBox(height: 60),
                Container(
                  width: 96,
                  height: 96,
                  padding: const EdgeInsets.all(18),
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: SvgPicture.asset('assets/logo.svg', fit: BoxFit.contain),
                ),
                const SizedBox(height: 24),
                const SizedBox(
                  width: 44,
                  height: 44,
                  child: CircularProgressIndicator(strokeWidth: 3),
                ),
                const SizedBox(height: 12),
                const Text('OPENHSD'),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

