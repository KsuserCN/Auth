<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-box">
      <!-- 左侧：Logo和说明 -->
      <div class="login-left">
        <div class="logo-section">
          <img src="/favicon.ico" alt="Logo" class="logo-icon" />
        </div>
        <h1 class="login-title">{{ t('ui.login.message001') }}</h1>
        <p class="login-description">{{ t('ui.login.message002') }}</p>
        <div v-if="desktopBridgeHint" class="desktop-login-hint" style="padding-bottom: 16px">
          {{ t('ui.login.message003') }}
        </div>
        <br v-if="desktopBridgeHint" />
        <div class="feature-list">
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lock />
            </el-icon>
            <span class="feature-text">{{ t('ui.login.message004') }}</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Lightning />
            </el-icon>
            <span class="feature-text">{{ t('ui.login.message005') }}</span>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon" :size="20">
              <Key />
            </el-icon>
            <span class="feature-text">{{ t('ui.login.message006') }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧：表单内容 -->
      <div class="login-right" :class="{ 'is-bootstrapping': loginBootstrapping }">
        <el-skeleton :loading="loginBootstrapping" animated>
          <template #template>
            <div class="bootstrap-state">
              <div class="bootstrap-skeleton">
                <el-skeleton-item variant="image" class="bootstrap-skeleton-hero" />
                <div class="bootstrap-skeleton-copy">
                  <el-skeleton-item variant="text" class="bootstrap-skeleton-title" />
                  <el-skeleton-item variant="text" class="bootstrap-skeleton-line" />
                  <el-skeleton-item variant="text" class="bootstrap-skeleton-line short" />
                </div>
              </div>
              <h2 class="bootstrap-title">{{ loginBootstrapTitle }}</h2>
              <p class="bootstrap-description">{{ loginBootstrapDescription }}</p>
            </div>
          </template>

          <template #default>
            <Transition
              :name="stepDirection === 'forward' ? 'step-slide-forward' : 'step-slide-backward'"
              mode="out-in"
            >
              <!-- 第一步：邮箱输入 -->
              <div v-if="step === 'email'" class="step-container" key="email">
                <h2 class="step-title">
                  {{
                    route.query.oauthBindProvider
                      ? t('ui.login.message007')
                      : t('ui.login.message008')
                  }}
                </h2>
                <p class="step-subtitle">
                  {{
                    route.query.oauthBindProvider
                      ? t('ui.login.message009')
                      : t('ui.login.message010')
                  }}
                </p>

                <el-form
                  ref="emailFormRef"
                  :model="emailInput"
                  :rules="emailRules"
                  label-position="top"
                >
                  <el-form-item prop="email">
                    <el-input
                      v-model="emailInput.email"
                      type="email"
                      :placeholder="t('ui.login.message011')"
                      @keyup.enter="goToNextStep"
                      autocomplete="username email"
                      autofocus
                    />
                  </el-form-item>
                </el-form>

                <div class="step-actions">
                  <el-button class="next-btn" @click="goToNextStep" :loading="validateLoading">
                    {{ t('ui.changePassword.message009') }}
                  </el-button>
                </div>

                <div v-if="!route.query.oauthBindProvider" class="create-account">
                  {{ t('ui.login.message012')
                  }}<router-link to="/register" class="link">{{
                    t('ui.login.message013')
                  }}</router-link>
                </div>

                <div v-if="desktopBridgeReady" class="desktop-bridge-card">
                  <div class="desktop-bridge-copy">
                    <div class="desktop-bridge-title">{{ t('ui.login.message014') }}</div>
                    <div
                      class="desktop-bridge-user"
                      :title="
                        desktopBridgeUser?.username && desktopBridgeUser?.email
                          ? `${desktopBridgeUser.username} · ${desktopBridgeUser.email}`
                          : desktopBridgeUser?.username || desktopBridgeUser?.email || ''
                      "
                    >
                      <div class="desktop-bridge-user-name">
                        {{ desktopBridgeUser?.username || desktopBridgeUser?.email }}
                      </div>
                      <div v-if="desktopBridgeUser?.email" class="desktop-bridge-user-email">
                        {{ desktopBridgeUser.email }}
                      </div>
                    </div>
                  </div>
                  <el-button
                    class="desktop-bridge-btn"
                    type="primary"
                    plain
                    @click="handleDesktopBridgeLogin"
                    :loading="desktopBridgeLoginLoading"
                  >
                    {{ t('ui.login.message015') }}
                  </el-button>
                </div>

                <div
                  v-if="mobileBridgeAvailable && !iosMobileBridgeAvailable"
                  class="mobile-bridge-card"
                >
                  <div class="mobile-bridge-copy">
                    <div class="mobile-bridge-title">
                      {{
                        mobileBridgeInWeChat
                          ? t('ui.login.message016')
                          : mobileBridgeAwaitingApproval
                            ? t('ui.login.message017')
                            : t('ui.login.message018')
                      }}
                    </div>
                    <div class="mobile-bridge-description">
                      {{
                        mobileBridgeInWeChat
                          ? t('ui.login.message019')
                          : mobileBridgeAwaitingApproval
                            ? t('ui.login.message020')
                            : t('ui.login.message021')
                      }}
                    </div>
                    <div v-if="mobileBridgeFallbackHint" class="mobile-bridge-tip">
                      {{ t('ui.login.message022') }}
                    </div>
                  </div>
                  <div class="mobile-bridge-actions">
                    <el-button
                      v-if="mobileBridgeInWeChat"
                      class="mobile-bridge-btn"
                      type="primary"
                      @click="copyCurrentLinkForExternalBrowser"
                    >
                      {{ t('ui.login.message023') }}
                    </el-button>
                    <el-button
                      v-else
                      class="mobile-bridge-btn"
                      type="primary"
                      @click="handleMobileBridgeLogin"
                      :loading="mobileBridgeLoading"
                    >
                      {{
                        mobileBridgeAwaitingApproval
                          ? t('ui.login.message024')
                          : t('ui.login.message025')
                      }}
                    </el-button>
                    <el-button
                      v-if="mobileBridgeAwaitingApproval"
                      class="mobile-bridge-cancel"
                      link
                      @click="clearMobileBridgeChallenge"
                    >
                      {{ t('ui.login.message026') }}
                    </el-button>
                  </div>
                </div>
              </div>

              <!-- 第二步：选择登录方式 -->
              <div
                v-else-if="step === 'method'"
                class="step-container"
                key="method"
                @keyup.enter="!methodSelecting && selectMethod('password')"
              >
                <h2 class="step-title">{{ t('ui.login.message027') }}</h2>

                <div class="email-confirm">
                  <span class="email-display">{{ emailInput.email }}</span>
                  <el-button link class="change-email-btn" @click="updateStep('email')">{{
                    t('ui.login.message028')
                  }}</el-button>
                </div>

                <div class="method-list">
                  <!-- 密码登录 -->
                  <div
                    class="method-option"
                    @click="!methodSelecting && selectMethod('password')"
                    :class="{ 'is-disabled': methodSelecting }"
                  >
                    <el-icon class="method-icon" :size="28">
                      <Lock />
                    </el-icon>
                    <div class="method-info">
                      <h3>{{ t('ui.login.message029') }}</h3>
                      <p>{{ t('ui.login.message030') }}</p>
                    </div>
                    <el-icon class="method-arrow" v-if="!methodSelecting">
                      <ArrowRight />
                    </el-icon>
                    <el-skeleton-item v-else variant="text" class="method-arrow-skeleton" />
                  </div>

                  <!-- 邮箱验证码登录 -->
                  <div
                    class="method-option"
                    @click="!methodSelecting && selectMethod('email')"
                    :class="{ 'is-disabled': methodSelecting }"
                  >
                    <el-icon class="method-icon" :size="28">
                      <Message />
                    </el-icon>
                    <div class="method-info">
                      <h3>{{ t('ui.login.message031') }}</h3>
                      <p>{{ t('ui.login.message032') }}</p>
                    </div>
                    <el-icon class="method-arrow" v-if="!methodSelecting">
                      <ArrowRight />
                    </el-icon>
                    <el-skeleton-item v-else variant="text" class="method-arrow-skeleton" />
                  </div>

                  <!-- Passkey 登录 -->
                </div>
              </div>

              <!-- 第三步：密码登录 -->
              <div v-else-if="step === 'password'" class="step-container" key="password">
                <h2 class="step-title">{{ t('ui.sensitiveVerificationDialog.message006') }}</h2>

                <div class="email-confirm">
                  <span class="email-display">{{ emailInput.email }}</span>
                  <el-button link class="change-email-btn" @click="backToMethod">{{
                    t('ui.login.message033')
                  }}</el-button>
                </div>

                <el-form
                  ref="passwordFormRef"
                  :model="passwordInput"
                  :rules="passwordRules"
                  label-position="top"
                >
                  <el-form-item prop="password">
                    <el-input
                      v-model="passwordInput.password"
                      type="password"
                      :placeholder="t('ui.loginOptions.message004')"
                      show-password
                      @keyup.enter="handlePasswordLogin"
                      autocomplete="current-password"
                      autofocus
                    />
                  </el-form-item>
                </el-form>

                <router-link to="/forgot-password" class="forgot-link">{{
                  t('ui.login.message034')
                }}</router-link>

                <div class="step-actions">
                  <el-button class="back-btn" @click="backToMethod">{{
                    t('ui.sensitiveVerificationDialog.message007')
                  }}</el-button>
                  <el-button
                    class="next-btn"
                    @click="handlePasswordLogin"
                    :loading="passwordLoading"
                  >
                    {{ t('ui.login.message001') }}
                  </el-button>
                </div>
              </div>

              <!-- 第三步：邮箱验证码登录 -->
              <div v-else-if="step === 'email-code'" class="step-container" key="email-code">
                <h2 class="step-title">{{ t('ui.changeEmail.message010') }}</h2>

                <div class="email-confirm">
                  <span class="email-display">{{ emailInput.email }}</span>
                  <el-button link class="change-email-btn" @click="backToMethod">{{
                    t('ui.login.message033')
                  }}</el-button>
                </div>

                <el-form
                  ref="codeFormRef"
                  :model="codeInput"
                  :rules="codeRules"
                  label-position="top"
                >
                  <el-form-item prop="code">
                    <el-input
                      v-model="codeInput.code"
                      :placeholder="t('ui.sensitiveVerificationDialog.message010')"
                      maxlength="6"
                      @input="codeInput.code = codeInput.code.replace(/[^\d]/g, '')"
                      @keyup.enter="handleEmailCodeLogin"
                      autofocus
                    />
                  </el-form-item>
                </el-form>

                <div class="code-actions">
                  <el-button v-if="!canResendCode" disabled class="resend-btn resend-disabled">
                    {{ codeCountdown }}{{ t('ui.changeEmail.message013') }}
                  </el-button>
                  <el-button v-else type="primary" @click="resendCode" class="resend-btn">{{
                    t('ui.sensitiveVerificationDialog.message012')
                  }}</el-button>
                </div>

                <div class="step-actions">
                  <el-button class="back-btn" @click="backToMethod">{{
                    t('ui.sensitiveVerificationDialog.message007')
                  }}</el-button>
                  <el-button class="next-btn" @click="handleEmailCodeLogin" :loading="codeLoading">
                    {{ t('ui.login.message001') }}
                  </el-button>
                </div>
              </div>

              <!-- 第三步：手机扫码登录 -->
              <div v-else-if="step === 'qr-login'" class="step-container" key="qr-login">
                <h2 class="step-title">{{ t('ui.mobileBridgeQrSidebarAction.message002') }}</h2>
                <p class="step-subtitle">{{ t('ui.login.message035') }}</p>

                <div class="qr-card">
                  <img
                    v-if="qrCodeImage"
                    :src="qrCodeImage"
                    :alt="t('ui.login.message036')"
                    class="qr-image"
                  />
                  <div v-else class="qr-placeholder">
                    {{ t('ui.mobileBridgeQrSidebarAction.message004') }}
                  </div>
                  <p class="qr-meta">
                    {{ t('ui.accountRecoveryCard.message009')
                    }}{{ Math.max(qrExpiresInSeconds, 0) }}
                    {{ t('ui.accountRecoveryCard.message010') }}
                  </p>
                  <p class="qr-tip">{{ t('ui.login.message037') }}</p>
                </div>

                <div class="step-actions">
                  <el-button class="back-btn" @click="updateStep('email')">{{
                    t('ui.sensitiveVerificationDialog.message007')
                  }}</el-button>
                  <el-button class="next-btn" @click="refreshQrChallenge" :loading="qrRefreshing">
                    {{ t('ui.mobileBridgeQrSidebarAction.message006') }}
                  </el-button>
                </div>
              </div>

              <!-- 第四步：选择 MFA 验证方式 -->
              <div v-else-if="step === 'mfa-method'" class="step-container" key="mfa-method">
                <h2 class="step-title">{{ t('ui.login.message038') }}</h2>
                <p class="step-subtitle">{{ t('ui.login.message039') }}</p>

                <div class="method-list">
                  <div
                    class="method-option"
                    @click="!methodSelecting && selectMfaMethod('totp')"
                    :class="{ 'is-disabled': methodSelecting || !mfaMethods.includes('totp') }"
                  >
                    <el-icon class="method-icon" :size="28">
                      <Lock />
                    </el-icon>
                    <div class="method-info">
                      <h3>{{ t('ui.login.message040') }}</h3>
                      <p>{{ t('ui.login.message041') }}</p>
                    </div>
                    <el-icon class="method-arrow" v-if="!methodSelecting">
                      <ArrowRight />
                    </el-icon>
                    <el-skeleton-item v-else variant="text" class="method-arrow-skeleton" />
                  </div>

                  <div
                    class="method-option"
                    @click="!methodSelecting && selectMfaMethod('passkey')"
                    :class="{
                      'is-disabled':
                        methodSelecting || !mfaMethods.includes('passkey') || !isPasskeySupported,
                    }"
                  >
                    <el-icon class="method-icon" :size="28">
                      <Key />
                    </el-icon>
                    <div class="method-info">
                      <h3>{{ t('ui.login.message042') }}</h3>
                      <p>{{ t('ui.login.message043') }}</p>
                    </div>
                    <el-icon class="method-arrow" v-if="!methodSelecting && isPasskeySupported">
                      <ArrowRight />
                    </el-icon>
                  </div>

                  <div
                    class="method-option"
                    @click="!methodSelecting && selectMfaMethod('qr')"
                    :class="{ 'is-disabled': methodSelecting || !mfaMethods.includes('qr') }"
                  >
                    <el-icon class="method-icon" :size="28">
                      <Message />
                    </el-icon>
                    <div class="method-info">
                      <h3>{{ t('ui.login.message044') }}</h3>
                      <p>{{ t('ui.login.message045') }}</p>
                    </div>
                    <el-icon class="method-arrow" v-if="!methodSelecting">
                      <ArrowRight />
                    </el-icon>
                  </div>
                </div>

                <div class="step-actions">
                  <el-button class="back-btn" @click="backToMFASource">{{
                    t('ui.sensitiveVerificationDialog.message007')
                  }}</el-button>
                </div>
              </div>

              <!-- 第四步：Passkey MFA 验证 -->
              <div v-else-if="step === 'mfa-passkey'" class="step-container" key="mfa-passkey">
                <h2 class="step-title">{{ t('ui.login.message046') }}</h2>
                <p class="step-subtitle">{{ t('ui.login.message047') }}</p>

                <div class="passkey-hint">
                  <p>{{ t('ui.login.message048') }}</p>
                </div>

                <div
                  v-if="canChooseMfaMethod()"
                  class="switch-method-section"
                  @click="updateStep('mfa-method')"
                >
                  <el-icon class="switch-method-icon">
                    <Refresh />
                  </el-icon>
                  <span>{{ t('ui.login.message049') }}</span>
                </div>

                <div class="step-actions">
                  <el-button class="back-btn" @click="backToMfaMethodOrSource">{{
                    t('ui.sensitiveVerificationDialog.message007')
                  }}</el-button>
                  <el-button
                    class="next-btn"
                    @click="handlePasskeyMfaVerify"
                    :loading="passkeyLoading"
                  >
                    {{
                      passkeyLoading
                        ? t('ui.sensitiveVerificationDialog.message014')
                        : t('ui.sensitiveVerificationDialog.message015')
                    }}
                  </el-button>
                </div>
              </div>

              <!-- 第四步：扫码 MFA 验证 -->
              <div v-else-if="step === 'mfa-qr'" class="step-container" key="mfa-qr">
                <h2 class="step-title">{{ t('ui.login.message050') }}</h2>
                <p class="step-subtitle">{{ t('ui.login.message051') }}</p>

                <div class="qr-card">
                  <img
                    v-if="qrCodeImage"
                    :src="qrCodeImage"
                    :alt="t('ui.login.message052')"
                    class="qr-image"
                  />
                  <div v-else class="qr-placeholder">
                    {{ t('ui.mobileBridgeQrSidebarAction.message004') }}
                  </div>
                  <p class="qr-meta">
                    {{ t('ui.accountRecoveryCard.message009')
                    }}{{ Math.max(qrExpiresInSeconds, 0) }}
                    {{ t('ui.accountRecoveryCard.message010') }}
                  </p>
                </div>

                <div class="step-actions">
                  <el-button class="back-btn" @click="backToMfaMethodOrSource">{{
                    t('ui.sensitiveVerificationDialog.message007')
                  }}</el-button>
                  <el-button class="next-btn" @click="refreshQrChallenge" :loading="qrRefreshing">
                    {{ t('ui.mobileBridgeQrSidebarAction.message006') }}
                  </el-button>
                </div>
              </div>

              <!-- 第四步：TOTP 验证 -->
              <div v-else-if="step === 'totp'" class="step-container" key="totp">
                <h2 class="step-title">{{ t('ui.sensitiveVerificationDialog.message015') }}</h2>
                <p class="step-subtitle">{{ t('ui.login.message053') }}</p>

                <div
                  class="totp-mode-switch"
                  role="tablist"
                  :aria-label="t('ui.desktopPasskeyBridge.message002')"
                >
                  <button
                    type="button"
                    class="totp-mode-chip"
                    :class="{ active: totpMode === 'totp' }"
                    :aria-pressed="totpMode === 'totp'"
                    @click="setTotpMode('totp')"
                  >
                    <span class="chip-title">{{
                      t('ui.sensitiveVerificationDialog.message020')
                    }}</span>
                    <span class="chip-meta">{{ t('ui.login.message054') }}</span>
                  </button>
                  <button
                    type="button"
                    class="totp-mode-chip"
                    :class="{ active: totpMode === 'recovery' }"
                    :aria-pressed="totpMode === 'recovery'"
                    @click="setTotpMode('recovery')"
                  >
                    <span class="chip-title">{{
                      t('ui.sensitiveVerificationDialog.message021')
                    }}</span>
                    <span class="chip-meta">{{ t('ui.login.message055') }}</span>
                  </button>
                </div>

                <el-form
                  ref="totpFormRef"
                  :model="totpInput"
                  :rules="totpRules"
                  label-position="top"
                >
                  <el-form-item prop="code">
                    <el-input
                      v-model="totpInput.code"
                      :placeholder="
                        totpMode === 'totp'
                          ? t('ui.sensitiveVerificationDialog.message022')
                          : t('ui.login.message056')
                      "
                      :maxlength="totpMode === 'totp' ? 6 : 8"
                      @input="handleTotpInput"
                      @keyup.enter="handleTotpVerify"
                      autofocus
                    />
                  </el-form-item>
                </el-form>

                <p class="totp-mode-hint">{{ totpModeHint }}</p>

                <div
                  v-if="canChooseMfaMethod()"
                  class="switch-method-section"
                  @click="updateStep('mfa-method')"
                >
                  <el-icon class="switch-method-icon">
                    <Refresh />
                  </el-icon>
                  <span>{{ t('ui.login.message049') }}</span>
                </div>

                <div class="step-actions">
                  <el-button class="back-btn" @click="backToMfaMethodOrSource">{{
                    t('ui.sensitiveVerificationDialog.message007')
                  }}</el-button>
                  <el-button class="next-btn" @click="handleTotpVerify" :loading="totpLoading">
                    {{ t('ui.sensitiveVerificationDialog.message008') }}
                  </el-button>
                </div>
              </div>
            </Transition>

            <div v-if="step === 'email'" class="extra-login">
              <div class="extra-title">{{ t('ui.login.message057') }}</div>
              <div class="extra-actions">
                <div class="extra-icons">
                  <button
                    class="icon-btn"
                    @click="handleQrLoginInit"
                    :aria-label="t('ui.login.message058')"
                    type="button"
                  >
                    <i class="fa-solid fa-qrcode" aria-hidden="true"></i>
                  </button>
                  <button
                    class="icon-btn"
                    @click="handleUnsupportedLogin(t('ui.loginOptions.message049'))"
                    :aria-label="t('ui.login.message059')"
                    type="button"
                  >
                    <i class="fa-brands fa-weixin" aria-hidden="true"></i>
                  </button>
                  <button
                    class="icon-btn"
                    @click="handleQQLogin"
                    :aria-label="t('ui.login.message060')"
                    type="button"
                  >
                    <i class="fa-brands fa-qq" aria-hidden="true"></i>
                  </button>
                  <button
                    class="icon-btn"
                    @click="handleAppleLogin"
                    :aria-label="t('ui.login.message061')"
                    type="button"
                  >
                    <i class="fa-brands fa-apple" aria-hidden="true"></i>
                  </button>
                  <!-- <button class="icon-btn" @click="handleMicrosoftLogin" aria-label="微软登录" type="button">
                    <i class="fa-brands fa-microsoft" aria-hidden="true"></i>
                  </button> -->
                  <button
                    class="icon-btn"
                    @click="handleGoogleLogin"
                    :aria-label="t('ui.login.message062')"
                    type="button"
                  >
                    <i class="fa-brands fa-google" aria-hidden="true"></i>
                  </button>
                </div>
                <el-button
                  class="extra-btn"
                  :loading="passkeyLoading"
                  :disabled="!isPasskeySupported"
                  @click="handlePasskeyLogin"
                >
                  {{ isPasskeySupported ? t('ui.login.message063') : t('ui.login.message064') }}
                </el-button>
              </div>
              <div v-if="iosMobileBridgeAvailable" class="ios-app-login">
                <el-button
                  class="extra-btn ios-app-login-btn"
                  :loading="mobileBridgeLoading"
                  @click="
                    mobileBridgeInWeChat
                      ? copyCurrentLinkForExternalBrowser()
                      : handleMobileBridgeLogin()
                  "
                >
                  {{
                    mobileBridgeAwaitingApproval
                      ? t('ui.login.message065')
                      : t('ui.login.message066')
                  }}
                </el-button>
                <p v-if="mobileBridgeInWeChat" class="ios-app-login-hint">
                  {{ t('ui.login.message067') }}
                </p>
                <p
                  v-else-if="mobileBridgeAwaitingApproval"
                  class="ios-app-login-hint"
                  role="status"
                >
                  {{ t('ui.login.message068') }}
                </p>
                <el-button
                  v-if="mobileBridgeAwaitingApproval"
                  class="mobile-bridge-cancel"
                  link
                  @click="clearMobileBridgeChallenge"
                  >{{ t('ui.login.message026') }}</el-button
                >
              </div>
            </div>
          </template>
        </el-skeleton>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { t, LocalizedError } from '@/i18n'

import { resolveApiResult } from '@/utils/apiResult'
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useDark } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { ArrowRight, Lock, Lightning, Key, Message, Refresh } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  passwordLogin,
  sendLoginCode,
  loginWithCode,
  getPasskeyAuthenticationOptions,
  verifyPasskeyAuthentication,
  verifyPasskeyForLoginMFA,
  verifyTOTPForLogin,
  buildGoogleAuthorizationUrl,
  buildMicrosoftAuthorizationUrl,
  buildQQAuthorizationUrl,
  bindPendingOAuthAccount,
  cancelMobileBridgeChallenge,
  exchangeSessionTransfer,
  initQrLogin,
  initQrMfa,
  pollQrStatus,
  refreshAccessToken,
  type MFAMethod,
  type MFAChallenge,
  type LoginResponse,
  type OAuthProvider,
  type QrChallengeStatusResponse,
} from '@/api/auth'
import {
  exchangeDesktopSessionToWeb,
  finalizeWebLogin,
  getDesktopBridgeStatus,
  storeWebSession,
  syncCurrentWebSessionToDesktop,
  type DesktopBridgeUser,
} from '@/utils/desktopBridge'
import { prepareAppleSignIn, signInWithApple } from '@/utils/appleSignIn'
import { isAppleAccountBindingRequested, savePendingAppleAccount } from '@/utils/appleAccountFlow'
import {
  buildMobileBridgeReturnUrl,
  createMobileBridgeLogin,
  fetchMobileBridgeStatus,
  getMobileBridgeChallengeIdFromUrl,
  isAndroidMobileBridgeSupported,
  isIOSMobileBridgeSupported,
  isWeChatInAppBrowser,
  launchMobileBridgeApp,
  readMobileBridgeFallbackFlag,
  stripMobileBridgeQuery,
} from '@/utils/mobileBridge'
import { getStoredAccessToken, hydrateSessionStorageFromSharedStorage } from '@/utils/authSession'
import {
  consumePostLoginRedirect,
  normalizePostLoginRedirect,
  persistPostLoginRedirect,
} from '@/utils/postLoginRedirect'
import { setRequestBaseUrl } from '@/utils/request'
import {
  isWebAuthnSupported,
  getPasskeyCredential,
  extractAuthenticationData,
} from '@/utils/webauthn'

// 表单引用
const emailFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const codeFormRef = ref<FormInstance>()
const totpFormRef = ref<FormInstance>()

// 路由
const router = useRouter()
const route = useRoute()

// 流程步骤
const step = ref<
  | 'email'
  | 'method'
  | 'password'
  | 'email-code'
  | 'qr-login'
  | 'mfa-method'
  | 'mfa-passkey'
  | 'mfa-qr'
  | 'totp'
>('email')
const stepDirection = ref<'forward' | 'backward'>('forward')

const stepOrder = [
  'email',
  'method',
  'password',
  'email-code',
  'qr-login',
  'mfa-method',
  'mfa-passkey',
  'mfa-qr',
  'totp',
]

const updateStep = (
  newStep:
    | 'email'
    | 'method'
    | 'password'
    | 'email-code'
    | 'qr-login'
    | 'mfa-method'
    | 'mfa-passkey'
    | 'mfa-qr'
    | 'totp',
) => {
  if (newStep !== 'qr-login' && newStep !== 'mfa-qr') {
    cleanupQrPolling()
  }
  const currentIndex = stepOrder.indexOf(step.value)
  const newIndex = stepOrder.indexOf(newStep)
  stepDirection.value = newIndex > currentIndex ? 'forward' : 'backward'
  step.value = newStep
}

// 使用 VueUse 的 useDark 同步暗黑模式
const isDark = useDark({
  storageKey: 'theme-preference',
  valueDark: 'dark',
  valueLight: 'light',
})

// 邮箱输入
const emailInput = ref({
  email: '',
})

const emailRules = computed(() => ({
  email: [
    { required: true, message: t('ui.login.message069'), trigger: 'blur' },
    { type: 'email', message: t('ui.login.message070'), trigger: 'blur' },
  ],
}))

// 密码输入
const passwordInput = ref({
  password: '',
})

const passwordRules = computed(() => ({
  password: [
    { required: true, message: t('ui.sensitiveVerificationDialog.message036'), trigger: 'blur' },
  ],
}))

// 验证码输入
const codeInput = ref({
  code: '',
})

const codeRules = computed(() => ({
  code: [
    { required: true, message: t('ui.sensitiveVerificationDialog.message037'), trigger: 'blur' },
    { len: 6, message: t('ui.sensitiveVerificationDialog.message038'), trigger: 'blur' },
  ],
}))

// TOTP 验证码输入
const totpInput = ref({
  code: '',
})

const totpMode = ref<'totp' | 'recovery'>('totp')

const totpModeHint = computed(() => {
  if (totpMode.value === 'totp') {
    return t('ui.login.message071')
  }

  return t('ui.login.message072')
})

const totpRules = computed(() => ({
  code: [
    { required: true, message: t('ui.sensitiveVerificationDialog.message039'), trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: (err?: Error) => void) => {
        const v = (value || '').trim()
        if (totpMode.value === 'totp' && !/^[0-9]{6}$/.test(v)) {
          callback(new LocalizedError('ui.sensitiveVerificationDialog.message040'))
          return
        }

        if (totpMode.value === 'recovery' && !/^[A-Z]{8}$/.test(v)) {
          callback(new LocalizedError('ui.sensitiveVerificationDialog.message041'))
          return
        }

        if (totpMode.value !== 'totp' && totpMode.value !== 'recovery') {
          callback(new LocalizedError('ui.login.message073'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}))

// 加载状态
const validateLoading = ref(false)
const passwordLoading = ref(false)
const codeLoading = ref(false)
const passkeyLoading = ref(false)
const methodSelecting = ref(false)
const totpLoading = ref(false)

// MFA 相关状态
const mfaChallenge = ref<MFAChallenge | null>(null)
const mfaSource = ref<
  | 'password'
  | 'email-code'
  | 'passkey'
  | 'qr-login'
  | 'qq'
  | 'github'
  | 'apple'
  | 'microsoft'
  | 'google'
  | null
>(null)
const mfaMethods = ref<MFAMethod[]>([])

const qrCodeImage = ref('')
const qrExpiresInSeconds = ref(0)
const qrRefreshing = ref(false)
const qrChallengeId = ref('')
const qrPollToken = ref('')
const qrMode = ref<'login' | 'mfa' | null>(null)
let qrPollingTimer: number | null = null

// 验证码倒计时
const codeCountdown = ref(0)
const canResendCode = ref(false)
let codeCountdownTimer: number | null = null

// Passkey 支持检测
const isPasskeySupported = ref(false)
const desktopBridgeUser = ref<DesktopBridgeUser | null>(null)
const desktopBridgeLoginLoading = ref(false)
const loginBootstrapping = ref(true)
const mobileBridgeLoading = ref(false)
const mobileBridgeAwaitingApproval = ref(false)
const mobileBridgeChallengeId = ref('')
const mobileBridgeAppLink = ref('')
let mobileBridgePollingTimer: number | null = null
let mobileBridgePolling = false

const desktopBridgeReady = computed(() => {
  return !!desktopBridgeUser.value && step.value === 'email'
})
const desktopBridgeHint = computed(() => route.query.desktopBridge === '1')
const iosMobileBridgeAvailable = computed(
  () => step.value === 'email' && isIOSMobileBridgeSupported(),
)
const mobileBridgeSupported = computed(
  () =>
    step.value === 'email' && (isAndroidMobileBridgeSupported() || isIOSMobileBridgeSupported()),
)
const mobileBridgeAvailable = computed(() => mobileBridgeSupported.value)
const mobileBridgeInWeChat = computed(() => mobileBridgeSupported.value && isWeChatInAppBrowser())
const mobileBridgeFallbackHint = computed(() => {
  const url = new URL(window.location.href)
  return readMobileBridgeFallbackFlag(url)
})
const hasPendingMobileBridgeChallenge = computed(() => {
  return mobileBridgeAwaitingApproval.value || !!mobileBridgeChallengeId.value
})
const loginBootstrapTitle = computed(() =>
  desktopBridgeHint.value
    ? t('ui.login.message074')
    : hasPendingMobileBridgeChallenge.value
      ? t('ui.login.message075')
      : t('ui.login.message076'),
)
const loginBootstrapDescription = computed(() =>
  desktopBridgeHint.value
    ? t('ui.login.message077')
    : hasPendingMobileBridgeChallenge.value
      ? t('ui.login.message078')
      : t('ui.login.message079'),
)

const getDirectPostLoginTarget = () => {
  return normalizePostLoginRedirect(
    typeof route.query.redirect === 'string' ? route.query.redirect : null,
  )
}

const clearStoredPostLoginRedirect = () => {
  consumePostLoginRedirect()
}

const getPendingOAuthBind = (): { provider: OAuthProvider; bindToken: string } | null => {
  const provider = route.query.oauthBindProvider
  const bindToken = route.query.oauthBindToken
  if (
    (provider === 'qq' ||
      provider === 'github' ||
      provider === 'microsoft' ||
      provider === 'google') &&
    typeof bindToken === 'string' &&
    bindToken.trim()
  ) {
    return {
      provider,
      bindToken: bindToken.trim(),
    }
  }
  return null
}

const bindPendingOAuthAfterLogin = async () => {
  const pending = getPendingOAuthBind()
  if (!pending) {
    return
  }
  const result = await bindPendingOAuthAccount(pending)
  ElMessage.success(resolveApiResult(result))
}

const navigateAfterLogin = async () => {
  if (route.query.oauthBindProvider === 'apple' || isAppleAccountBindingRequested()) {
    await router.replace('/oauth/apple/continue?mode=bind')
    return
  }
  await bindPendingOAuthAfterLogin()

  const directTarget = getDirectPostLoginTarget()
  if (directTarget) {
    clearStoredPostLoginRedirect()
    await router.replace(directTarget)
    return
  }

  const storedTarget = consumePostLoginRedirect()
  await router.replace(storedTarget || '/home/overview')
}

const persistCurrentPostLoginRedirect = () => {
  if (route.query.oauthBindProvider || isAppleAccountBindingRequested()) return
  persistPostLoginRedirect(typeof route.query.redirect === 'string' ? route.query.redirect : null)
}

const cleanupMobileBridgePolling = () => {
  if (mobileBridgePollingTimer !== null) {
    clearInterval(mobileBridgePollingTimer)
    mobileBridgePollingTimer = null
  }
}

const replaceRouteWithoutMobileBridgeQuery = async () => {
  const nextUrl = stripMobileBridgeQuery(new URL(window.location.href))
  const nextQuery = Object.fromEntries(nextUrl.searchParams.entries())
  await router.replace({
    path: route.path,
    query: nextQuery,
  })
}

const resetMobileBridgeState = async (clearRoute = false) => {
  cleanupMobileBridgePolling()
  mobileBridgeAwaitingApproval.value = false
  mobileBridgeChallengeId.value = ''
  mobileBridgeAppLink.value = ''
  if (clearRoute) {
    await replaceRouteWithoutMobileBridgeQuery()
  }
}

const exchangeApprovedMobileBridge = async (transferCode: string) => {
  const response = await exchangeSessionTransfer(transferCode, 'web')
  await finalizeWebLogin({
    accessToken: response.accessToken,
    user: response.user,
    syncDesktop: false,
  })
  ElMessage.success(t('ui.login.message080'))
  await navigateAfterLogin()
}

const pollMobileBridgeStatus = async () => {
  if (!mobileBridgeChallengeId.value) {
    cleanupMobileBridgePolling()
    return
  }

  if (mobileBridgePolling || document.hidden) return
  const challengeId = mobileBridgeChallengeId.value
  mobileBridgePolling = true
  let exchanging = false

  try {
    const status = await fetchMobileBridgeStatus(challengeId)
    if (challengeId !== mobileBridgeChallengeId.value || document.hidden) return
    if (status.status === 'pending') {
      mobileBridgeAwaitingApproval.value = true
      return
    }

    cleanupMobileBridgePolling()

    if (status.status === 'approved' && status.transferCode) {
      exchanging = true
      mobileBridgeChallengeId.value = ''
      mobileBridgeAppLink.value = ''
      mobileBridgeAwaitingApproval.value = false
      mobileBridgeLoading.value = true
      await exchangeApprovedMobileBridge(status.transferCode)
      return
    }

    if (status.status === 'cancelled') {
      ElMessage.info(t('ui.login.message081'))
    } else {
      ElMessage.warning(t('ui.login.message082'))
    }
    await resetMobileBridgeState(true)
  } catch (error: unknown) {
    if (!exchanging && challengeId !== mobileBridgeChallengeId.value) return
    cleanupMobileBridgePolling()
    const message = error instanceof Error ? error.message : t('ui.login.message083')
    ElMessage.error(message)
    await resetMobileBridgeState(false)
  } finally {
    mobileBridgePolling = false
    if (exchanging) mobileBridgeLoading.value = false
  }
}

const startMobileBridgePolling = () => {
  cleanupMobileBridgePolling()
  mobileBridgeAwaitingApproval.value = true
  mobileBridgePollingTimer = window.setInterval(() => {
    void pollMobileBridgeStatus()
  }, 2000)
  void pollMobileBridgeStatus()
}

const resumePendingMobileBridge = async (): Promise<boolean> => {
  const challengeId = getMobileBridgeChallengeIdFromUrl(new URL(window.location.href))
  if (!challengeId) {
    if (mobileBridgeFallbackHint.value) {
      await replaceRouteWithoutMobileBridgeQuery()
    }
    return false
  }

  mobileBridgeChallengeId.value = challengeId
  const appLink = new URL('/app/bridge-login', window.location.origin)
  appLink.searchParams.set('challengeId', challengeId)
  appLink.searchParams.set('returnUrl', buildMobileBridgeReturnUrl(challengeId))
  mobileBridgeAppLink.value = appLink.toString()
  if (mobileBridgeFallbackHint.value) {
    ElMessage.warning(t('ui.login.message084'))
  }
  startMobileBridgePolling()
  return true
}

const normalizeMfaMethods = (
  methods: MFAChallenge['methods'],
  fallbackMethod: MFAChallenge['method'],
): MFAMethod[] => {
  const normalized = (methods ?? []).filter(
    (item): item is MFAMethod => item === 'totp' || item === 'passkey' || item === 'qr',
  )

  if (normalized.length > 0) {
    return [...new Set(normalized)]
  }

  return fallbackMethod === 'passkey' || fallbackMethod === 'qr' ? [fallbackMethod] : ['totp']
}

const canChooseMfaMethod = () => {
  const selectable = mfaMethods.value.filter((method) => {
    if (method === 'passkey') {
      return isPasskeySupported.value
    }
    return true
  })
  return selectable.length > 1
}

const cleanupQrPolling = () => {
  if (qrPollingTimer !== null) {
    clearInterval(qrPollingTimer)
    qrPollingTimer = null
  }
}

const buildMfaChallengeFromStatus = (status: QrChallengeStatusResponse): MFAChallenge | null => {
  if (!status.mfaChallengeId) {
    return null
  }

  const normalizedMethods = (status.methods ?? []).filter(
    (item): item is MFAMethod => item === 'totp' || item === 'passkey' || item === 'qr',
  )
  const fallbackMethod: MFAMethod =
    status.method === 'passkey' || status.method === 'qr' ? status.method : 'totp'

  return {
    challengeId: status.mfaChallengeId,
    method: fallbackMethod,
    methods: normalizedMethods.length > 0 ? normalizedMethods : [fallbackMethod],
  }
}

const handleQrApproved = async (status: QrChallengeStatusResponse) => {
  if (qrMode.value === 'login') {
    if (status.transferCode) {
      const response = await exchangeSessionTransfer(status.transferCode, 'web')
      const desktopSynced = await finalizeWebLogin({
        accessToken: response.accessToken,
        user: response.user,
      })
      ElMessage.success(desktopSynced ? t('ui.login.message085') : t('ui.login.message086'))
      await navigateAfterLogin()
      return
    }

    const nextMfa = buildMfaChallengeFromStatus(status)
    if (nextMfa) {
      startMfaFlow(nextMfa, 'qr-login')
      return
    }

    ElMessage.error(t('ui.login.message087'))
    return
  }

  if (qrMode.value === 'mfa') {
    if (!status.transferCode) {
      ElMessage.error(t('ui.login.message088'))
      return
    }
    const response = await exchangeSessionTransfer(status.transferCode, 'web')
    const desktopSynced = await finalizeWebLogin({
      accessToken: response.accessToken,
      user: response.user,
    })
    ElMessage.success(desktopSynced ? t('ui.login.message089') : t('ui.login.message090'))
    await navigateAfterLogin()
  }
}

const pollQrChallenge = async () => {
  if (!qrChallengeId.value || !qrPollToken.value) return

  try {
    const status = await pollQrStatus(qrChallengeId.value, qrPollToken.value)
    qrExpiresInSeconds.value = status.expiresInSeconds || 0

    if (status.status === 'pending') {
      return
    }

    cleanupQrPolling()

    if (status.status === 'approved') {
      await handleQrApproved(status)
      return
    }
    if (status.status === 'rejected') {
      ElMessage.error(t('ui.login.message091'))
      return
    }
    ElMessage.warning(t('ui.sensitiveVerificationDialog.message046'))
  } catch (error) {
    cleanupQrPolling()
    console.error('QR polling failed:', error)
  }
}

const startQrPolling = () => {
  cleanupQrPolling()
  qrPollingTimer = window.setInterval(() => {
    void pollQrChallenge()
  }, 2000)
  void pollQrChallenge()
}

const initQrFlow = async (mode: 'login' | 'mfa') => {
  try {
    qrRefreshing.value = true
    qrMode.value = mode

    const payload =
      mode === 'login'
        ? await initQrLogin()
        : await initQrMfa((mfaChallenge.value?.challengeId || '').trim())

    qrChallengeId.value = payload.challengeId
    qrPollToken.value = payload.pollToken
    qrExpiresInSeconds.value = payload.expiresInSeconds
    qrCodeImage.value = await QRCode.toDataURL(payload.qrText, { width: 240, margin: 1 })

    updateStep(mode === 'login' ? 'qr-login' : 'mfa-qr')
    startQrPolling()
  } catch (error) {
    console.error('Init QR flow failed:', error)
    ElMessage.error(mode === 'login' ? t('ui.login.message092') : t('ui.login.message093'))
  } finally {
    qrRefreshing.value = false
  }
}

const refreshQrChallenge = async () => {
  if (!qrMode.value) return
  await initQrFlow(qrMode.value)
}

const handleQrLoginInit = async () => {
  await initQrFlow('login')
}

const startQrMfaFlow = async () => {
  if (!mfaChallenge.value?.challengeId) {
    ElMessage.error(t('ui.login.message094'))
    return
  }
  await initQrFlow('mfa')
}

const handleTotpInput = (value: string) => {
  if (totpMode.value === 'totp') {
    totpInput.value.code = value.replace(/[^\d]/g, '').slice(0, 6)
    return
  }

  totpInput.value.code = value
    .replace(/[^a-zA-Z]/g, '')
    .toUpperCase()
    .slice(0, 8)
}

const setTotpMode = (mode: 'totp' | 'recovery') => {
  if (totpMode.value === mode) return
  totpMode.value = mode
  totpInput.value.code = ''
}

const startMfaFlow = (
  challenge: MFAChallenge,
  source:
    | 'password'
    | 'email-code'
    | 'passkey'
    | 'qr-login'
    | 'qq'
    | 'github'
    | 'apple'
    | 'microsoft'
    | 'google'
    | null,
) => {
  mfaChallenge.value = challenge
  mfaSource.value = source
  mfaMethods.value = normalizeMfaMethods(challenge.methods, challenge.method)

  ElMessage.info(t('ui.login.message095'))

  const passkeyAvailable = mfaMethods.value.includes('passkey') && isPasskeySupported.value
  const totpAvailable = mfaMethods.value.includes('totp')
  const qrAvailable = mfaMethods.value.includes('qr')

  if (challenge.method === 'passkey' && passkeyAvailable) {
    updateStep('mfa-passkey')
    return
  }

  if (challenge.method === 'totp' && totpAvailable) {
    updateStep('totp')
    return
  }

  if (challenge.method === 'qr' && qrAvailable) {
    void startQrMfaFlow()
    return
  }

  if (passkeyAvailable) {
    updateStep('mfa-passkey')
    return
  }

  if (totpAvailable) {
    updateStep('totp')
    return
  }

  if (qrAvailable) {
    void startQrMfaFlow()
    return
  }

  if (mfaMethods.value.includes('passkey') && !mfaMethods.value.includes('totp')) {
    ElMessage.error(t('ui.login.message096'))
    updateStep('email')
    return
  }

  ElMessage.error(t('ui.login.message097'))
  updateStep('email')
}

onMounted(() => {
  void prepareAppleSignIn().catch(() => {})
  isPasskeySupported.value = isWebAuthnSupported()
  void initializeLoginView()

  const challengeId = route.query.challengeId
  const method = route.query.method
  const methods = route.query.methods
  const mfaFrom = route.query.mfaFrom

  if (typeof challengeId === 'string' && challengeId.trim()) {
    const normalizedMethod: MFAMethod = method === 'passkey' || method === 'qr' ? method : 'totp'
    const queryMethods = typeof methods === 'string' ? methods.split(',') : []
    const challenge: MFAChallenge = {
      challengeId: challengeId.trim(),
      method: normalizedMethod,
      methods:
        queryMethods.length > 0
          ? queryMethods.filter(
              (item): item is MFAMethod => item === 'totp' || item === 'passkey' || item === 'qr',
            )
          : undefined,
    }

    const source =
      mfaFrom === 'qq' ||
      mfaFrom === 'github' ||
      mfaFrom === 'microsoft' ||
      mfaFrom === 'google' ||
      mfaFrom === 'apple'
        ? mfaFrom
        : null

    startMfaFlow(challenge, source)
  }
})

const shouldBlockLoginBootstrap = (): boolean => {
  const transferCode =
    typeof route.query.transferCode === 'string' ? route.query.transferCode.trim() : ''
  const mobileBridgeChallengeId = getMobileBridgeChallengeIdFromUrl(new URL(window.location.href))

  if (desktopBridgeHint.value || transferCode || mobileBridgeChallengeId) {
    return true
  }

  return Boolean(getStoredAccessToken())
}

const initializeLoginView = async () => {
  const shouldBlock = shouldBlockLoginBootstrap()
  loginBootstrapping.value = shouldBlock

  try {
    const apiBaseUrl =
      typeof route.query.apiBaseUrl === 'string' ? route.query.apiBaseUrl.trim() : ''
    if (apiBaseUrl) {
      setRequestBaseUrl(apiBaseUrl)
    }

    const transferCode =
      typeof route.query.transferCode === 'string' ? route.query.transferCode.trim() : ''
    if (transferCode) {
      await handleTransferCodeLogin(transferCode)
      return
    }

    const resumedSession = await resumeExistingWebSession()
    if (resumedSession) {
      if (desktopBridgeHint.value) {
        const synced = await syncCurrentWebSessionToDesktop()
        ElMessage.success(synced ? t('ui.login.message098') : t('ui.login.message099'))
      }
      await navigateAfterLogin()
      return
    }

    if (await resumePendingMobileBridge()) {
      return
    }

    if (desktopBridgeHint.value) {
      await loadDesktopBridgeStatus()
      return
    }

    void loadDesktopBridgeStatus()
  } finally {
    if (shouldBlock) {
      loginBootstrapping.value = false
    }
  }
}

const resumeExistingWebSession = async (): Promise<boolean> => {
  const currentAccessToken = getStoredAccessToken()
  if (currentAccessToken) {
    hydrateSessionStorageFromSharedStorage()
    return true
  }

  try {
    const refreshed = await refreshAccessToken()
    if (!refreshed.accessToken) {
      return false
    }
    storeWebSession(refreshed.accessToken)
    return true
  } catch {
    return false
  }
}

const loadDesktopBridgeStatus = async () => {
  if (getStoredAccessToken()) {
    return
  }

  const status = await getDesktopBridgeStatus()
  if (status?.authenticated && status.user) {
    desktopBridgeUser.value = status.user
    return
  }

  desktopBridgeUser.value = null
}

const handleTransferCodeLogin = async (transferCode: string) => {
  try {
    desktopBridgeLoginLoading.value = true
    const response = await exchangeSessionTransfer(transferCode, 'web')
    await finalizeWebLogin({
      accessToken: response.accessToken,
      user: response.user,
      syncDesktop: false,
    })
    ElMessage.success(t('ui.login.message100'))
    await navigateAfterLogin()
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : t('ui.login.message101')
    ElMessage.error(message)
    await loadDesktopBridgeStatus()
  } finally {
    desktopBridgeLoginLoading.value = false
  }
}

// ===== 第一步：邮箱验证 =====

const goToNextStep = async () => {
  try {
    validateLoading.value = true
    await emailFormRef.value?.validate()
    updateStep('method')
  } catch {
    // 表单验证失败，Element Plus 会显示错误提示
  } finally {
    validateLoading.value = false
  }
}

// ===== 第二步：选择登录方式 =====

const selectMethod = async (method: 'password' | 'email') => {
  if (methodSelecting.value) return

  methodSelecting.value = true

  try {
    if (method === 'password') {
      await new Promise((resolve) => setTimeout(resolve, 200))
      updateStep('password')
    } else if (method === 'email') {
      await sendCode()
    }
  } finally {
    methodSelecting.value = false
  }
}

const backToMethod = () => {
  updateStep('method')
  codeInput.value.code = ''
  cleanupCodeCountdown()
}

// ===== 第三步：密码登录 =====

const handlePasswordLogin = async () => {
  try {
    await passwordFormRef.value?.validate()
    passwordLoading.value = true

    // 调用密码登录接口
    const response = await passwordLogin({
      email: emailInput.value.email,
      password: passwordInput.value.password,
    })

    // 检查是否需要 MFA 验证
    if ('challengeId' in response) {
      startMfaFlow(response as MFAChallenge, 'password')
    } else {
      // 直接登录成功
      const loginResp = response as LoginResponse
      const desktopSynced = await finalizeWebLogin({
        accessToken: loginResp.accessToken,
        user: loginResp.user,
      })
      ElMessage.success(desktopSynced ? t('ui.login.message102') : t('ui.login.message103'))
      await navigateAfterLogin()
    }
  } catch (error: unknown) {
    // 错误已经在 request.ts 中处理并显示
    console.error('Password login failed:', error)
  } finally {
    passwordLoading.value = false
  }
}

// ===== 第三步：邮箱验证码登录 =====

const sendCode = async () => {
  try {
    // 调用发送登录验证码接口
    await sendLoginCode({
      email: emailInput.value.email,
    })

    ElMessage.success(t('ui.sensitiveVerificationDialog.message049'))
    updateStep('email-code')
    codeInput.value.code = ''
    startCodeCountdown()
  } catch (error: unknown) {
    // 错误已经在 request.ts 中处理并显示
    console.error('Send code failed:', error)
  }
}

const resendCode = async () => {
  try {
    codeLoading.value = true

    // 调用发送登录验证码接口
    await sendLoginCode({
      email: emailInput.value.email,
    })

    ElMessage.success(t('ui.sensitiveVerificationDialog.message050'))
    codeInput.value.code = ''
    startCodeCountdown()
  } catch (error: unknown) {
    // 错误已经在 request.ts 中处理并显示
    console.error('Resend code failed:', error)
  } finally {
    codeLoading.value = false
  }
}

const startCodeCountdown = () => {
  canResendCode.value = false
  codeCountdown.value = 60
  cleanupCodeCountdown()

  codeCountdownTimer = window.setInterval(() => {
    codeCountdown.value--
    if (codeCountdown.value <= 0) {
      cleanupCodeCountdown()
      canResendCode.value = true
    }
  }, 1000)
}

const cleanupCodeCountdown = () => {
  if (codeCountdownTimer) {
    clearInterval(codeCountdownTimer)
    codeCountdownTimer = null
  }
}

const handleEmailCodeLogin = async () => {
  try {
    await codeFormRef.value?.validate()
    codeLoading.value = true

    // 调用邮箱验证码登录接口
    const response = await loginWithCode({
      email: emailInput.value.email,
      code: codeInput.value.code,
    })

    // 检查是否需要 MFA 验证
    if ('challengeId' in response) {
      startMfaFlow(response as MFAChallenge, 'email-code')
    } else {
      // 直接登录成功
      const loginResp = response as LoginResponse
      const desktopSynced = await finalizeWebLogin({
        accessToken: loginResp.accessToken,
        user: loginResp.user,
      })
      ElMessage.success(desktopSynced ? t('ui.login.message102') : t('ui.login.message103'))
      await navigateAfterLogin()
    }
  } catch (error: unknown) {
    // 错误已经在 request.ts 中处理并显示
    console.error('Email code login failed:', error)
  } finally {
    codeLoading.value = false
  }
}

// ===== 第三步：Passkey登录 =====

const handlePasskeyLogin = async () => {
  if (passkeyLoading.value) return
  if (!isPasskeySupported.value) {
    ElMessage.error(t('ui.sensitiveVerificationDialog.message051'))
    return
  }

  try {
    passkeyLoading.value = true

    // 1. 获取 Passkey 认证选项
    const options = await getPasskeyAuthenticationOptions()

    // 2. 调用浏览器 WebAuthn API 进行认证
    const credential = await getPasskeyCredential({
      challenge: options.challenge,
      timeout: options.timeout,
      rpId: options.rpId,
      userVerification: options.userVerification,
      allowCredentials: options.allowCredentials,
    })

    if (!credential) {
      throw new LocalizedError('ui.sensitiveVerificationDialog.message052')
    }

    // 3. 提取认证数据
    const authData = extractAuthenticationData(credential)

    // 4. 验证凭证
    const response = await verifyPasskeyAuthentication(options.challengeId, authData)

    // 检查是否需要 MFA 验证
    if ('challengeId' in response) {
      startMfaFlow(response as MFAChallenge, 'passkey')
    } else {
      // 直接登录成功
      const loginResp = response as LoginResponse
      const desktopSynced = await finalizeWebLogin({
        accessToken: loginResp.accessToken,
        user: loginResp.user,
      })
      ElMessage.success(desktopSynced ? t('ui.login.message104') : t('ui.login.message105'))
      await navigateAfterLogin()
    }
  } catch (error: unknown) {
    // 处理不同类型的错误
    if (error instanceof Error) {
      if (error.name === 'NotAllowedError') {
        ElMessage.error(t('ui.sensitiveVerificationDialog.message053'))
        return
      }
      if (error.name === 'NotSupportedError') {
        ElMessage.error(t('ui.addPasskey.message021'))
        return
      }
      if (error.name === 'SecurityError') {
        ElMessage.error(t('ui.addPasskey.message022'))
        return
      }
      if (error.name === 'InvalidStateError') {
        ElMessage.error(t('ui.login.message106'))
        return
      }
      console.error('Passkey login failed:', error)
    } else {
      console.error('Passkey login failed:', error)
    }

    ElMessage.error(t('ui.login.message107'))
  } finally {
    passkeyLoading.value = false
  }
}

const handleUnsupportedLogin = (provider: string) => {
  ElMessage.info(t('ui.login.message108', { arg0: provider.toUpperCase() }))
}

// ===== QQ 登录 =====

const generateRandomString = (): string => {
  const array = new Uint8Array(16)
  crypto.getRandomValues(array)
  return Array.from(array, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

const toBase64Url = (bytes: Uint8Array): string => {
  const base64 = btoa(String.fromCharCode(...bytes))
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

const generatePkcePair = async (): Promise<{ codeVerifier: string; codeChallenge: string }> => {
  const verifierBytes = new Uint8Array(32)
  crypto.getRandomValues(verifierBytes)
  const codeVerifier = toBase64Url(verifierBytes)

  const hashed = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(codeVerifier))
  const codeChallenge = toBase64Url(new Uint8Array(hashed))

  return { codeVerifier, codeChallenge }
}

const handleQQLogin = async () => {
  try {
    persistCurrentPostLoginRedirect()
    // 生成随机字符串
    const randomString = generateRandomString()

    // 获取 VITE_DEBUG_STATE
    const debugState = import.meta.env.VITE_DEBUG_STATE || 'dev'

    // 组合 state 参数：校验参数;操作类型;环境
    const state = `${randomString};login;${debugState}`

    // 存储到 sessionStorage
    sessionStorage.setItem('qq_oauth_state', state)

    // 构建 QQ 授权 URL
    const authUrl = buildQQAuthorizationUrl(state)

    // 跳转到 QQ 授权页
    window.location.href = authUrl
  } catch (error: unknown) {
    console.error('QQ login failed:', error)
    ElMessage.error(t('ui.login.message109'))
  }
}

const handleAppleLogin = async () => {
  try {
    persistCurrentPostLoginRedirect()
    const response = await signInWithApple('login')
    if (response.needBind) {
      if (desktopBridgeHint.value) {
        ElMessage.error(t('ui.login.message110'))
        return
      }
      savePendingAppleAccount(response)
      await router.push('/oauth/apple/continue')
    } else if (response.challengeId) {
      await router.push({
        path: '/login',
        query: {
          ...route.query,
          challengeId: response.challengeId,
          method: response.method || 'totp',
          methods: response.methods?.join(','),
          mfaFrom: 'apple',
        },
      })
      startMfaFlow(
        {
          challengeId: response.challengeId,
          method: response.method || 'totp',
          methods: response.methods,
        },
        'apple',
      )
    } else if (response.accessToken) {
      await finalizeWebLogin({ accessToken: response.accessToken, user: response.user })
      await navigateAfterLogin()
    } else {
      throw new LocalizedError('ui.login.message111')
    }
  } catch (error: unknown) {
    ElMessage.error(error instanceof Error ? error.message : t('ui.login.message112'))
  }
}

const handleMicrosoftLogin = async () => {
  try {
    persistCurrentPostLoginRedirect()
    const randomString = generateRandomString()
    const debugState = import.meta.env.VITE_DEBUG_STATE || 'dev'
    const state = `${randomString};login;${debugState}`
    const { codeVerifier, codeChallenge } = await generatePkcePair()

    sessionStorage.setItem('microsoft_oauth_state', state)
    sessionStorage.setItem('microsoft_oauth_code_verifier', codeVerifier)

    const authUrl = buildMicrosoftAuthorizationUrl(state, codeChallenge)
    window.location.href = authUrl
  } catch (error: unknown) {
    console.error('Microsoft login failed:', error)
    ElMessage.error(t('ui.login.message113'))
  }
}

const handleGoogleLogin = async () => {
  try {
    persistCurrentPostLoginRedirect()
    const randomString = generateRandomString()
    const debugState = import.meta.env.VITE_DEBUG_STATE || 'dev'
    const state = `${randomString};login;${debugState}`

    sessionStorage.setItem('google_oauth_state', state)

    const authUrl = buildGoogleAuthorizationUrl(state)
    window.location.href = authUrl
  } catch (error: unknown) {
    console.error('Google login failed:', error)
    ElMessage.error(t('ui.login.message114'))
  }
}

// ===== 第四步：TOTP 验证 =====

const selectMfaMethod = async (method: MFAMethod) => {
  if (methodSelecting.value) return

  methodSelecting.value = true

  try {
    if (method === 'totp' && !mfaMethods.value.includes('totp')) {
      ElMessage.error(t('ui.login.message115'))
      return
    }

    if (method === 'passkey') {
      if (!mfaMethods.value.includes('passkey')) {
        ElMessage.error(t('ui.login.message116'))
        return
      }
      if (!isPasskeySupported.value) {
        ElMessage.error(t('ui.sensitiveVerificationDialog.message051'))
        return
      }
      updateStep('mfa-passkey')
      return
    }

    if (method === 'qr') {
      if (!mfaMethods.value.includes('qr')) {
        ElMessage.error(t('ui.login.message117'))
        return
      }
      await startQrMfaFlow()
      return
    }

    updateStep('totp')
  } finally {
    methodSelecting.value = false
  }
}

const handlePasskeyMfaVerify = async () => {
  if (passkeyLoading.value) return

  if (!mfaChallenge.value) {
    ElMessage.error(t('ui.login.message094'))
    return
  }

  if (!isPasskeySupported.value) {
    ElMessage.error(t('ui.sensitiveVerificationDialog.message051'))
    return
  }

  try {
    passkeyLoading.value = true

    const options = await getPasskeyAuthenticationOptions()
    const credential = await getPasskeyCredential({
      challenge: options.challenge,
      timeout: options.timeout,
      rpId: options.rpId,
      userVerification: options.userVerification,
      allowCredentials: options.allowCredentials,
    })

    if (!credential) {
      throw new LocalizedError('ui.sensitiveVerificationDialog.message052')
    }

    const authData = extractAuthenticationData(credential)
    const response = await verifyPasskeyForLoginMFA({
      mfaChallengeId: mfaChallenge.value.challengeId,
      passkeyChallengeId: options.challengeId,
      ...authData,
    })

    const desktopSynced = await finalizeWebLogin({
      accessToken: response.accessToken,
      user: response.user,
    })
    ElMessage.success(desktopSynced ? t('ui.login.message118') : t('ui.login.message119'))
    await navigateAfterLogin()
  } catch (error: unknown) {
    if (error instanceof Error) {
      if (error.name === 'NotAllowedError') {
        ElMessage.error(t('ui.sensitiveVerificationDialog.message053'))
        return
      }
      if (error.name === 'NotSupportedError') {
        ElMessage.error(t('ui.addPasskey.message021'))
        return
      }
      if (error.name === 'SecurityError') {
        ElMessage.error(t('ui.addPasskey.message022'))
        return
      }
      if (error.name === 'InvalidStateError') {
        ElMessage.error(t('ui.login.message106'))
        return
      }
      console.error('Passkey MFA verification failed:', error)
    } else {
      console.error('Passkey MFA verification failed:', error)
    }
  } finally {
    passkeyLoading.value = false
  }
}

const handleTotpVerify = async () => {
  try {
    await totpFormRef.value?.validate()
    totpLoading.value = true

    if (!mfaChallenge.value) {
      ElMessage.error(t('ui.login.message094'))
      return
    }

    const normalizedInput = totpInput.value.code.trim()

    // 调用 TOTP 验证接口
    const response = await verifyTOTPForLogin(
      totpMode.value === 'recovery'
        ? {
            challengeId: mfaChallenge.value.challengeId,
            recoveryCode: normalizedInput.toUpperCase(),
          }
        : {
            challengeId: mfaChallenge.value.challengeId,
            code: normalizedInput,
          },
    )

    // 存储 Access Token 到 sessionStorage
    const desktopSynced = await finalizeWebLogin({
      accessToken: response.accessToken,
      user: response.user,
    })
    ElMessage.success(desktopSynced ? t('ui.login.message118') : t('ui.login.message119'))
    await navigateAfterLogin()
  } catch (error: unknown) {
    // 错误已经在 request.ts 中处理并显示
    console.error('TOTP verification failed:', error)
  } finally {
    totpLoading.value = false
  }
}

const backToMFASource = () => {
  const source = mfaSource.value
  mfaChallenge.value = null
  mfaSource.value = null
  mfaMethods.value = []
  qrMode.value = null
  qrChallengeId.value = ''
  qrPollToken.value = ''
  qrCodeImage.value = ''
  qrExpiresInSeconds.value = 0
  totpInput.value.code = ''
  totpMode.value = 'totp'

  // 根据 MFA 来源返回到对应的页面
  switch (source) {
    case 'password':
      updateStep('password')
      break
    case 'email-code':
      updateStep('email-code')
      break
    case 'passkey':
      // Passkey 登录没有中间步骤，直接返回到 email
      emailInput.value.email = ''
      updateStep('email')
      break
    case 'qr-login':
      updateStep('email')
      break
    default:
      updateStep('email')
  }
}

const handleDesktopBridgeLogin = async () => {
  if (desktopBridgeLoginLoading.value) {
    return
  }

  try {
    desktopBridgeLoginLoading.value = true
    const response = await exchangeDesktopSessionToWeb()
    await finalizeWebLogin({
      accessToken: response.accessToken,
      user: response.user,
      syncDesktop: false,
    })
    ElMessage.success(t('ui.login.message120'))
    await navigateAfterLogin()
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : t('ui.login.message121')
    ElMessage.error(message)
    desktopBridgeUser.value = null
  } finally {
    desktopBridgeLoginLoading.value = false
  }
}

const clearMobileBridgeChallenge = async () => {
  if (mobileBridgeChallengeId.value) {
    await cancelMobileBridgeChallenge(mobileBridgeChallengeId.value).catch(() => undefined)
  }
  await resetMobileBridgeState(true)
}

const copyCurrentLinkForExternalBrowser = async () => {
  const text = window.location.href
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const tempInput = document.createElement('textarea')
      tempInput.value = text
      tempInput.style.position = 'fixed'
      tempInput.style.opacity = '0'
      document.body.appendChild(tempInput)
      tempInput.select()
      document.execCommand('copy')
      document.body.removeChild(tempInput)
    }
    ElMessage.success(t('ui.login.message122'))
  } catch {
    ElMessage.error(t('ui.login.message123'))
  }
}

const handleMobileBridgeLogin = async () => {
  if (mobileBridgeLoading.value) {
    return
  }
  if (mobileBridgeInWeChat.value) {
    ElMessage.warning(t('ui.login.message124'))
    return
  }

  try {
    if (mobileBridgeAwaitingApproval.value && mobileBridgeAppLink.value) {
      launchMobileBridgeApp(mobileBridgeAppLink.value)
      return
    }
    mobileBridgeLoading.value = true
    const challenge = await createMobileBridgeLogin()
    mobileBridgeChallengeId.value = challenge.challengeId
    mobileBridgeAppLink.value = challenge.appLink

    const nextReturnUrl = buildMobileBridgeReturnUrl(challenge.challengeId)
    const nextUrl = new URL(nextReturnUrl)
    await router.replace({
      path: route.path,
      query: Object.fromEntries(nextUrl.searchParams.entries()),
    })

    mobileBridgeAwaitingApproval.value = true
    startMobileBridgePolling()
    launchMobileBridgeApp(challenge.appLink)
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : t('ui.login.message125')
    ElMessage.error(message)
    await resetMobileBridgeState(false)
  } finally {
    mobileBridgeLoading.value = false
  }
}

const backToMfaMethodOrSource = () => {
  if (canChooseMfaMethod()) {
    updateStep('mfa-method')
    return
  }

  backToMFASource()
}

// ===== 生命周期 =====

const handleKeyPress = (e: KeyboardEvent) => {
  const target = e.target as HTMLElement
  const isInput = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA'

  if (step.value === 'method' && e.key === 'Enter' && !isInput && !methodSelecting.value) {
    e.preventDefault()
    selectMethod('password')
  }
}

const resumeMobileBridgePolling = () => {
  if (!document.hidden && mobileBridgeChallengeId.value) {
    void pollMobileBridgeStatus()
  }
}

onMounted(() => {
  isPasskeySupported.value = Boolean(window.PublicKeyCredential)
  window.addEventListener('keypress', handleKeyPress)
  document.addEventListener('visibilitychange', resumeMobileBridgePolling)
  window.addEventListener('pageshow', resumeMobileBridgePolling)
})

onBeforeUnmount(() => {
  cleanupCodeCountdown()
  cleanupQrPolling()
  cleanupMobileBridgePolling()
  mobileBridgeChallengeId.value = ''
  window.removeEventListener('keypress', handleKeyPress)
  document.removeEventListener('visibilitychange', resumeMobileBridgePolling)
  window.removeEventListener('pageshow', resumeMobileBridgePolling)
})
</script>

<style scoped>
/* 全局样式修复 - 禁用滚动并铺满视口 */
:global(html),
:global(body),
:global(#app) {
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
}

.login-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f5f5 0%, #fafafa 100%);
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  overflow: hidden;
}

.login-container.dark {
  background: linear-gradient(135deg, #1a1a1a 0%, #2d2d2d 100%);
}

.login-box {
  width: 100%;
  max-width: 1000px;
  display: flex;
  background: var(--el-bg-color);
  border-radius: 20px;
  box-shadow:
    0 8px 24px rgba(0, 0, 0, 0.12),
    0 16px 40px rgba(0, 0, 0, 0.08);
  animation: slideIn 0.6s ease-out;
  backdrop-filter: blur(10px);
  border: 1px solid var(--el-border-color-light);
  overflow: hidden;
  transition: all 2s cubic-bezier(0.4, 0, 0.2, 1);
}

.login-left {
  flex: 1;
  padding: 60px 48px;
  background: linear-gradient(135deg, #fff8f0, #fffbf5);
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.login-container.dark .login-left {
  background: var(--el-bg-color-overlay) !important;
}

.login-right {
  flex: 1;
  padding: 48px 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  background: var(--el-bg-color);
  transition: all 2s cubic-bezier(0.4, 0, 0.2, 1);
}

.login-right.is-bootstrapping {
  align-items: center;
}

.bootstrap-state {
  width: 100%;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  text-align: center;
  padding: 48px 24px;
}

.bootstrap-skeleton {
  width: min(320px, 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.bootstrap-skeleton-hero {
  width: 108px;
  height: 108px;
  border-radius: 24px;
}

.bootstrap-skeleton-copy {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bootstrap-skeleton-title {
  width: 56%;
  height: 18px;
}

.bootstrap-skeleton-line {
  width: 100%;
  height: 14px;
}

.bootstrap-skeleton-line.short {
  width: 74%;
}

.bootstrap-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.bootstrap-description {
  max-width: 360px;
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Step 过渡动画 */
.step-slide-forward-enter-active,
.step-slide-forward-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.step-slide-forward-enter-from {
  opacity: 0;
  transform: translateX(40px);
}

.step-slide-forward-leave-to {
  opacity: 0;
  transform: translateX(-40px);
}

.step-slide-backward-enter-active,
.step-slide-backward-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.step-slide-backward-enter-from {
  opacity: 0;
  transform: translateX(-40px);
}

.step-slide-backward-leave-to {
  opacity: 0;
  transform: translateX(40px);
}

/* Logo部分 */
.logo-section {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 32px;
}

.logo-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: block;
}

.login-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin: 0 0 12px 0;
  letter-spacing: -0.5px;
}

.login-description {
  font-size: 15px;
  color: var(--el-text-color-regular);
  margin: 0 0 32px 0;
  font-weight: 400;
  line-height: 1.6;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.feature-icon {
  color: var(--el-color-primary);
  min-width: 24px;
}

/* 步骤容器 */
.step-container {
  width: 100%;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

/* Vue Transition - Fade */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.step-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 8px 0;
  letter-spacing: -0.3px;
}

.step-subtitle {
  font-size: 16px;
  color: var(--el-text-color-regular);
  margin: 0 0 32px 0;
  font-weight: 400;
}

/* 表单样式 */
:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-form-item__label) {
  display: none;
}

:deep(.el-input__wrapper) {
  border: 1.5px solid var(--el-border-color);
  border-radius: 12px;
  background: var(--el-fill-color-light);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  padding: 14px 16px;
}

:deep(.el-input__wrapper:hover) {
  border-color: var(--el-border-color-hover);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
  background: var(--el-bg-color);
}

:deep(.el-input__wrapper.is-focus) {
  border-color: var(--el-color-primary);
  box-shadow: 0 4px 12px rgba(255, 185, 15, 0.25);
  background: var(--el-bg-color);
}

:deep(.el-input__inner) {
  font-size: 16px;
  color: var(--el-text-color-primary);
}

:deep(.el-input__inner::placeholder) {
  color: var(--el-text-color-placeholder);
}

/* 邮箱确认 */
.email-confirm {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32px;
  padding: 16px 20px;
  background: var(--el-fill-color-light);
  border-radius: 12px;
  border: 1px solid var(--el-border-color);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
}

.email-display {
  font-size: 16px;
  color: var(--el-text-color-primary);
  font-weight: 500;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.change-email-btn,
.change-email-btn:hover {
  color: var(--el-color-primary);
  font-weight: 500;
  flex-shrink: 0;
  margin-left: 16px;
}

.code-hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
}

.totp-mode-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 12px;
}

.totp-mode-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-lighter);
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.totp-mode-chip:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
}

.totp-mode-chip.active {
  border-color: var(--el-color-primary);
  background: rgba(255, 185, 15, 0.08);
  box-shadow: 0 0 0 2px rgba(255, 185, 15, 0.12);
}

.chip-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.chip-meta {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 999px;
  padding: 2px 8px;
}

.totp-mode-chip.active .chip-meta {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}

.totp-mode-hint {
  margin: 2px 0 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

/* 登录方式列表 */
.method-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.method-option {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  background: var(--el-fill-color-light);
  border: 1.5px solid var(--el-border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.method-option:hover {
  background: var(--el-bg-color);
  border-color: var(--el-color-primary);
  box-shadow: 0 8px 16px rgba(255, 185, 15, 0.15);
  transform: translateY(-2px);
}

.method-option:active {
  transform: scale(0.98);
}

.method-option.is-disabled {
  opacity: 0.6;
  cursor: not-allowed;
  pointer-events: none;
}

.method-icon {
  color: var(--el-color-primary);
  flex-shrink: 0;
}

.method-info {
  flex: 1;
}

.method-info h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.method-info p {
  margin: 4px 0 0 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.method-arrow {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  font-size: 20px;
}

.method-arrow-skeleton {
  width: 18px;
  height: 18px;
  border-radius: 999px;
  flex-shrink: 0;
}

/* 验证码操作 */
.code-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  width: 100%;
}

.resend-btn {
  flex: 1;
  font-size: 14px;
  height: 44px;
  border-radius: 10px;
  font-weight: 500;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.resend-btn.resend-disabled {
  background: var(--el-fill-color);
  color: var(--el-text-color-placeholder);
  border: 1.5px solid var(--el-border-color);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
  cursor: not-allowed;
}

.resend-btn:not(.resend-disabled) {
  background: linear-gradient(
    135deg,
    var(--el-color-primary) 0%,
    var(--el-color-primary-light-3) 100%
  );
  color: white;
  border: none;
  box-shadow: 0 4px 12px rgba(255, 185, 15, 0.2);
}

.resend-btn:not(.resend-disabled):hover {
  box-shadow: 0 8px 16px rgba(255, 185, 15, 0.3);
  transform: translateY(-2px);
}

.resend-btn:not(.resend-disabled):active {
  transform: translateY(0) scale(0.98);
}

/* 按钮 */
.step-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-bottom: 24px;
}

.next-btn,
.back-btn {
  min-width: 100px;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.next-btn {
  background: var(--el-color-primary);
  border: none;
  color: white;
  flex: 1;
}

.next-btn:hover {
  box-shadow: 0 8px 16px rgba(255, 185, 15, 0.3);
  transform: translateY(-2px);
}

.next-btn:active {
  transform: translateY(0) scale(0.98);
}

.next-btn:disabled {
  background: linear-gradient(135deg, #e5a908 0%, #f5c90f 100%);
  opacity: 0.6;
  box-shadow: none;
  cursor: not-allowed;
}

.back-btn {
  background: var(--el-bg-color);
  border: 1.5px solid var(--el-border-color);
  color: var(--el-text-color-primary);
}

.back-btn:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.back-btn:active {
  transform: scale(0.98);
}

/* 链接 */
.forgot-link {
  display: inline-block;
  font-size: 14px;
  color: var(--el-color-primary);
  text-decoration: none;
  margin-bottom: 24px;
  transition: all 0.2s;
}

.forgot-link:hover {
  color: #ffd700;
  text-decoration: underline;
}

.create-account {
  text-align: center;
  font-size: 14px;
  color: var(--el-text-color-regular);
  margin-top: 24px;
}

.create-account .link {
  margin-inline-start: 4px;
  color: var(--el-color-primary);
  text-decoration: none;
  font-weight: 500;
  margin-left: 4px;
  transition: all 0.2s;
}

.create-account .link:hover {
  color: #ffd700;
  text-decoration: underline;
}

.desktop-login-hint {
  margin: 18px 0 0;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 185, 15, 0.12);
  border: 1px solid rgba(255, 185, 15, 0.2);
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
}

.desktop-bridge-card {
  margin-top: 18px;
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid rgba(255, 185, 15, 0.28);
  background: linear-gradient(135deg, rgba(255, 185, 15, 0.12) 0%, rgba(255, 241, 204, 0.72) 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.desktop-bridge-copy {
  flex: 1 1 auto;
  min-width: 0;
}

.desktop-bridge-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.desktop-bridge-user {
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  max-width: 100%;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.desktop-bridge-user-name {
  min-width: 0;
  font-weight: 600;
  color: var(--el-text-color-primary);
  line-height: 1.5;
}

.desktop-bridge-user-email {
  min-width: 0;
  line-height: 1.5;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.desktop-bridge-btn {
  flex-shrink: 0;
}

.mobile-bridge-card {
  margin-top: 14px;
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid rgba(32, 100, 224, 0.16);
  background: linear-gradient(160deg, rgba(238, 246, 255, 0.94) 0%, rgba(255, 255, 255, 0.98) 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.mobile-bridge-copy {
  flex: 1 1 auto;
  min-width: 0;
}

.mobile-bridge-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.mobile-bridge-description {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.mobile-bridge-tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: #2156b7;
}

.mobile-bridge-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

.mobile-bridge-btn {
  min-width: 116px;
}

.mobile-bridge-cancel {
  padding: 0;
}

@media (max-width: 820px) {
  .desktop-bridge-card {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .mobile-bridge-card {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .desktop-bridge-btn {
    margin-left: auto;
  }

  .mobile-bridge-actions {
    width: 100%;
    align-items: stretch;
  }
}

@media (max-width: 640px) {
  .desktop-bridge-btn {
    width: 100%;
    margin-left: 0;
  }

  .mobile-bridge-btn {
    width: 100%;
  }

  .mobile-bridge-actions {
    align-items: stretch;
  }
}

/* 输入框样式 */
:deep(.el-input__wrapper) {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

:deep(.el-input:focus-within .el-input__wrapper) {
  box-shadow:
    0 0 0 2px rgba(255, 185, 15, 0.1),
    0 1px 8px rgba(255, 185, 15, 0.2);
  border-color: var(--el-color-primary);
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 2px rgba(255, 185, 15, 0.1),
    0 1px 8px rgba(255, 185, 15, 0.2);
  border-color: var(--el-color-primary);
}

:deep(.el-input input) {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.el-input input::placeholder) {
  color: #d3d3d3;
  transition: color 0.3s ease;
}

:deep(.el-input input:focus::placeholder) {
  color: #ccc;
}

/* Form Item样式 */
:deep(.el-form-item) {
  transition: all 0.3s ease;
  margin-bottom: 20px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #202124;
  transition: color 0.3s ease;
}

/* Passkey 提示 */
.passkey-hint {
  background: var(--el-fill-color-light);
  border: 1.5px solid var(--el-border-color);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;

  text-align: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
}

.passkey-hint p {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.qr-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 16px;
  margin-bottom: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  background: var(--el-fill-color-lighter);
}

.qr-image {
  width: 220px;
  height: 220px;
  border-radius: 10px;
  background: #fff;
  padding: 10px;
  box-sizing: border-box;
}

.qr-placeholder {
  width: 220px;
  height: 220px;
  border-radius: 10px;
  border: 1px dashed var(--el-border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.qr-meta {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.qr-tip {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-regular);
  text-align: center;
}

/* 选择其他验证方式容器 */
.switch-method-section {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  margin: 12px 0 24px 0;
  background: linear-gradient(135deg, rgba(255, 185, 15, 0.05) 0%, rgba(255, 185, 15, 0.02) 100%);
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.switch-method-section:hover {
  background: linear-gradient(135deg, rgba(255, 185, 15, 0.08) 0%, rgba(255, 185, 15, 0.04) 100%);
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 4px 12px rgba(255, 185, 15, 0.12);
}

.switch-method-section:active {
  transform: scale(0.99);
}

.switch-method-icon {
  color: var(--el-color-primary);
  font-size: 18px;
  flex-shrink: 0;
}

.switch-method-btn {
  margin: 0;
  padding: 0;
  color: var(--el-color-primary);
  font-size: 14px;
  font-weight: 500;
  height: auto;
  line-height: 1.5;
}

.switch-method-btn:hover {
  color: var(--el-color-primary);
}

/* 额外登录方式 */
.extra-login {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-light);
}

.extra-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
}

.extra-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.extra-icons {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  max-width: 100%;
  gap: 16px;
  align-items: center;
}

.icon-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.3s ease;
}

.icon-btn:hover {
  opacity: 0.8;
}

.icon-btn i {
  font-size: 28px;
  color: var(--el-text-color-secondary);
  opacity: 0.7;
  transition:
    opacity 0.3s ease,
    color 0.3s ease;
}

.icon-btn:hover i {
  opacity: 0.9;
  color: var(--el-color-primary);
}

.dark .icon-btn i {
  opacity: 0.85;
}

.extra-btn {
  height: auto;
  min-height: 40px;
  max-width: 100%;
  margin-left: auto;
  padding-block: 10px;
  border-radius: 10px;
  font-weight: 500;
  white-space: normal;
}

.ios-app-login {
  margin-top: 10px;
  text-align: center;
}

.ios-app-login-btn {
  width: 100%;
}

.ios-app-login-hint {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

/* 响应式设计 */
@media (max-width: 900px) {
  .login-box {
    max-width: 100%;
    flex-direction: column;
  }

  .login-left {
    border-right: none;
    border-bottom: 1px solid #f0f0f0;
    padding: 40px 32px;
  }

  .login-right {
    padding: 40px 32px;
  }

  .login-title {
    font-size: 28px;
  }

  .step-title {
    font-size: 24px;
  }

  .login-container {
    padding: 20px;
  }
}

@media (max-width: 600px) {
  .login-box {
    border-radius: 12px;
  }

  .login-left {
    padding: 12px 16px;
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: flex-start;
    gap: 10px;
    flex: none;
    width: 100%;
    border-bottom: none;
    min-height: 0;
  }

  .login-left .login-description,
  .login-left .feature-list,
  .login-left .desktop-login-hint,
  .login-left br {
    display: none !important;
  }

  .logo-section {
    margin-bottom: 0;
    display: flex;
    align-items: center;
    flex-shrink: 0;
  }

  .logo-icon {
    width: 34px;
    height: 34px;
    border-radius: 10px;
  }

  .login-right {
    padding: 12px 16px 24px;
    flex: 1 1 auto;
  }

  .login-title {
    font-size: 28px;
    margin-bottom: 0;
    line-height: 1.2;
    text-align: left;
  }

  .step-title {
    font-size: 20px;
  }

  .login-description {
    margin-bottom: 24px;
  }

  .feature-list {
    gap: 12px;
  }

  .feature-item {
    font-size: 13px;
  }

  .feature-icon {
    font-size: 18px;
  }

  .step-actions {
    flex-direction: column-reverse;
  }

  .next-btn,
  .back-btn {
    width: 100%;
  }

  .email-confirm {
    flex-direction: column;
    align-items: flex-start;
  }

  .change-email-btn {
    margin-left: 0;
    margin-top: 12px;
  }

  .method-list {
    gap: 8px;
  }

  .method-option {
    padding: 12px;
  }

  .method-icon {
    font-size: 24px;
  }

  .method-info h3 {
    font-size: 14px;
  }

  .method-info p {
    font-size: 12px;
  }

  .extra-actions {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .extra-icons {
    width: 100%;
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 8px;
    justify-items: center;
  }

  .icon-btn {
    width: 100%;
    min-width: 0;
    padding: 6px 0;
  }

  .icon-btn i {
    font-size: 24px;
  }

  .extra-btn {
    width: 100%;
    max-width: 100%;
  }

  .qr-image,
  .qr-placeholder {
    width: 180px;
    height: 180px;
  }
}
</style>
