import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/openhsd_models.dart';

class TokenStorage {
  static const String _tokenKey = 'openhsd_token';
  static const String _userKey = 'openhsd_user';

  Future<String> getToken() async {
    final sp = await SharedPreferences.getInstance();
    return sp.getString(_tokenKey) ?? '';
  }

  Future<void> setToken(String token) async {
    final sp = await SharedPreferences.getInstance();
    await sp.setString(_tokenKey, token);
  }

  Future<void> removeToken() async {
    final sp = await SharedPreferences.getInstance();
    await sp.remove(_tokenKey);
  }

  Future<void> setUser(OpenHsdUser user) async {
    final sp = await SharedPreferences.getInstance();
    await sp.setString(_userKey, jsonEncode(user.toJson()));
  }

  Future<OpenHsdUser?> getUser() async {
    final sp = await SharedPreferences.getInstance();
    final raw = sp.getString(_userKey);
    if (raw == null || raw.isEmpty) return null;
    try {
      return OpenHsdUser.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    } catch (_) {
      return null;
    }
  }

  Future<void> clear() async {
    final sp = await SharedPreferences.getInstance();
    await sp.remove(_tokenKey);
    await sp.remove(_userKey);
  }
}

