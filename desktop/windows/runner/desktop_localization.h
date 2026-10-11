#ifndef RUNNER_DESKTOP_LOCALIZATION_H_
#define RUNNER_DESKTOP_LOCALIZATION_H_

#include <windows.h>
#include <string>
#include <atomic>

// Shared by window/tray and authentication bridges. Flutter supplies the
// effective locale after loading its persisted setting and on every change.
inline std::atomic_bool& DesktopUsesChinese() {
  static std::atomic_bool chinese([] {
    wchar_t locale[LOCALE_NAME_MAX_LENGTH] = {};
    return GetUserDefaultLocaleName(locale, LOCALE_NAME_MAX_LENGTH) > 0 &&
           locale[0] == L'z' && locale[1] == L'h';
  }());
  return chinese;
}
inline void SetDesktopLocale(const std::string& locale) {
  DesktopUsesChinese().store(locale == "zh-Hans");
}
inline const char* DesktopText(const char* chinese, const char* english) {
  return DesktopUsesChinese().load() ? chinese : english;
}
inline const wchar_t* DesktopText(const wchar_t* chinese, const wchar_t* english) {
  return DesktopUsesChinese().load() ? chinese : english;
}

#endif  // RUNNER_DESKTOP_LOCALIZATION_H_
