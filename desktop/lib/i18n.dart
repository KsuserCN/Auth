import 'dart:convert';
import 'dart:io';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import 'l10n/generated/app_localizations.dart';

/// Language choices are local to this installation, independent of an account.
enum DesktopLanguage {
  system('system'),
  simplifiedChinese('zh-Hans'),
  english('en');

  const DesktopLanguage(this.storageValue);
  final String storageValue;

  static DesktopLanguage parse(Object? value) => values.firstWhere(
    (DesktopLanguage language) => language.storageValue == value,
    orElse: () => DesktopLanguage.system,
  );
}

Locale resolveDesktopLocale(DesktopLanguage preference, List<Locale> locales) {
  if (preference == DesktopLanguage.simplifiedChinese) {
    return const Locale.fromSubtags(languageCode: 'zh', scriptCode: 'Hans');
  }
  if (preference == DesktopLanguage.english) return const Locale('en');
  // The device's preferred language determines the UI; unsupported languages
  // fall back to English. All Chinese variants use Simplified Chinese.
  return locales.isNotEmpty && locales.first.languageCode.toLowerCase() == 'zh'
      ? const Locale.fromSubtags(languageCode: 'zh', scriptCode: 'Hans')
      : const Locale('en');
}

class DesktopLanguageStore {
  DesktopLanguageStore({Directory? directory}) : _directory = directory;
  final Directory? _directory;

  static Directory applicationSupportDirectory() {
    final Map<String, String> environment = Platform.environment;
    if (Platform.isMacOS) {
      return Directory(
        '${environment['HOME'] ?? Directory.current.path}/Library/Application Support/ksuser_auth_desktop',
      );
    }
    if (Platform.isWindows) {
      final String base =
          environment['APPDATA'] ??
          environment['LOCALAPPDATA'] ??
          Directory.current.path;
      return Directory('$base${Platform.pathSeparator}ksuser_auth_desktop');
    }
    final String base =
        environment['XDG_CONFIG_HOME'] ??
        '${environment['HOME'] ?? Directory.current.path}/.config';
    return Directory('$base${Platform.pathSeparator}ksuser_auth_desktop');
  }

  File get _file => File(
    '${(_directory ?? applicationSupportDirectory()).path}${Platform.pathSeparator}language_preference.json',
  );

  Future<DesktopLanguage> load() async {
    try {
      final Object? value = jsonDecode(await _file.readAsString());
      return DesktopLanguage.parse(value is Map ? value['language'] : null);
    } catch (_) {
      return DesktopLanguage.system;
    }
  }

  Future<void> save(DesktopLanguage language) async {
    final File file = _file;
    await file.parent.create(recursive: true);
    await file.writeAsString(
      jsonEncode(<String, String>{'language': language.storageValue}),
      flush: true,
    );
  }
}

class DesktopLocaleController extends ChangeNotifier
    with WidgetsBindingObserver {
  DesktopLocaleController({
    DesktopLanguage preference = DesktopLanguage.system,
    DesktopLanguageStore? store,
    List<Locale> Function()? deviceLocales,
  }) : _preference = preference,
       _store = store ?? DesktopLanguageStore(),
       _deviceLocales =
           deviceLocales ?? (() => PlatformDispatcher.instance.locales);

  final DesktopLanguageStore _store;
  final List<Locale> Function() _deviceLocales;
  DesktopLanguage _preference;
  bool _observing = false;
  Future<void>? _pendingSave;

  DesktopLanguage get preference => _preference;
  Locale get locale => resolveDesktopLocale(_preference, _deviceLocales());
  String get languageTag => locale.languageCode == 'zh' ? 'zh-Hans' : 'en';

  void observeDeviceLocales() {
    if (_observing) return;
    WidgetsBinding.instance.addObserver(this);
    _observing = true;
  }

  Future<void> setLanguage(DesktopLanguage language) {
    _preference = language;
    notifyListeners();
    // Serialize writes so rapid switches cannot persist an older selection.
    final Future<void> save = _pendingSave == null
        ? _store.save(language)
        : _pendingSave!.then((_) => _store.save(language));
    _pendingSave = save.catchError((Object error, StackTrace stack) {
      debugPrint('Could not save the desktop language preference: $error');
    });
    return _pendingSave!;
  }

  @override
  void didChangeLocales(List<Locale>? locales) {
    if (_preference == DesktopLanguage.system) notifyListeners();
  }

  @override
  void dispose() {
    if (_observing) WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}

DesktopLocaleController desktopLocaleController = DesktopLocaleController();

AppLocalizations get desktopL10n =>
    lookupAppLocalizations(desktopLocaleController.locale);
