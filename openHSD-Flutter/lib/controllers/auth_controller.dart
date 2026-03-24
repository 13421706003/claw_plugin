import 'package:flutter/foundation.dart';

import '../core/api_client.dart';
import '../core/token_storage.dart';
import '../models/openhsd_models.dart';

class AuthController extends ChangeNotifier {
  final TokenStorage _storage;

  OpenHsdUser? _user;
  bool _initDone = false;

  AuthController({TokenStorage? storage}) : _storage = storage ?? TokenStorage();

  OpenHsdUser? get user => _user;
  bool get isLoggedIn => _user != null && _user!.token.isNotEmpty;
  bool get initDone => _initDone;

  Future<void> init() async {
    _user = await _storage.getUser();
    _initDone = true;
    notifyListeners();
  }

  Future<void> login({
    required String username,
    required String password,
  }) async {
    final api = ApiClient();
    final u = await api.login(username, password);
    _user = u;
    await _storage.setToken(u.token);
    await _storage.setUser(u);
    notifyListeners();
  }

  Future<void> register({
    required String username,
    required String password,
  }) async {
    final api = ApiClient();
    await api.register(username, password);
  }

  String get token => _user?.token ?? '';

  Future<void> logout() async {
    _user = null;
    await _storage.clear();
    notifyListeners();
  }
}

