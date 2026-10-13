<template>
  <div class="application-info-content">
    <div class="info-brand">
      <img src="/favicon.ico" alt="Ksuser" />
      <span>{{ t('ui.mainLayout.message001') }}</span>
    </div>

    <div class="info-app-header">
      <img
        v-if="logoUrl"
        :src="logoUrl"
        :alt="appName"
        class="info-app-logo"
      />
      <div v-else class="info-app-logo info-app-logo-fallback" aria-hidden="true">
        {{ appName.slice(0, 1).toUpperCase() || '?' }}
      </div>
      <div class="info-app-meta">
        <div class="info-app-trust" :aria-label="t('ui.applicationInfo.trustLabels')">
          <span v-if="mode === 'official'" class="info-trust-tag info-trust-tag--official">
            {{ t('ui.applicationInfo.official') }}
          </span>
          <template v-else>
            <span v-if="subjectName" class="info-trust-tag info-trust-tag--subject">
              {{ t('ui.applicationInfo.subjectName', { name: subjectName }) }}
            </span>
            <span v-else class="info-trust-tag info-trust-tag--unknown">
              {{ t('ui.applicationInfo.subjectUnknown') }}
            </span>
            <span
              v-if="verificationLabel"
              class="info-trust-tag"
              :class="`info-trust-tag--${subjectVerificationType}`"
            >
              {{ verificationLabel }}
            </span>
          </template>
        </div>
        <h1>{{ appName }}</h1>
        <p v-if="destination" class="info-destination">{{ destination }}</p>
      </div>
    </div>

    <div class="info-scopes">
      <strong>{{ t('ui.officialQr.scopes') }}</strong>
      <ul>
        <li v-for="scope in requestedScopes" :key="scope">
          {{ applicationQrScopeLabel(mode, scope) }}
        </li>
      </ul>
    </div>

    <div v-if="consoleApplication" class="info-admin-warning" role="note">
      <i class="fa-solid fa-shield-halved" aria-hidden="true"></i>
      <span>{{ t('ui.officialQr.adminOnly') }}</span>
    </div>

    <p class="info-privacy-note">{{ t('ui.officialQr.identityNotice') }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { t } from '@/i18n'
import {
  applicationQrScopeLabel,
  type ApplicationAuthorizationMode,
} from '@/utils/applicationQr'

const props = defineProps<{
  appName: string
  logoUrl?: string
  redirectUri: string
  requestedScopes: string[]
  mode: ApplicationAuthorizationMode
  consoleApplication?: boolean
  subjectName?: string | null
  subjectVerificationType?: string | null
}>()

const destination = computed(() => {
  try {
    return new URL(props.redirectUri).origin
  } catch {
    return props.redirectUri
  }
})
const verificationLabel = computed(() => {
  const key = props.subjectVerificationType
  if (key === 'personal') return t('ui.applicationInfo.personal')
  if (key === 'enterprise') return t('ui.applicationInfo.enterprise')
  if (key === 'admin') return t('ui.applicationInfo.admin')
  return ''
})
</script>

<style scoped>
.application-info-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  color: var(--el-text-color-primary);
}
.info-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 36px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  font-weight: 600;
}
.info-brand img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}
.info-app-header {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}
.info-app-logo {
  flex: 0 0 64px;
  width: 64px;
  height: 64px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
  background: #fff;
  object-fit: contain;
}
.info-app-logo-fallback {
  display: grid;
  place-items: center;
  border-color: transparent;
  background: linear-gradient(140deg, #1f9d6c 0%, #2fbf7d 100%);
  color: #fff;
  font-size: 26px;
  font-weight: 700;
}
.info-app-meta {
  min-width: 0;
}
.info-app-trust {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 7px;
}
.info-trust-tag {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  padding: 3px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
  background: var(--el-fill-color-extra-light);
  color: var(--el-text-color-secondary);
  font-size: 11px;
  font-weight: 600;
  line-height: 1.35;
  overflow-wrap: anywhere;
}
.info-trust-tag--official {
  border-color: rgb(220 150 28 / 42%);
  background: rgb(220 150 28 / 12%);
  color: var(--passport-link-color, #805c00);
}
.info-trust-tag--personal {
  border-color: rgb(46 160 112 / 35%);
  background: rgb(46 160 112 / 10%);
  color: var(--el-color-success);
}
.info-trust-tag--enterprise {
  border-color: rgb(220 150 28 / 42%);
  background: rgb(220 150 28 / 12%);
  color: var(--passport-link-color, #805c00);
}
.info-trust-tag--admin {
  border-color: rgb(220 76 76 / 38%);
  background: rgb(220 76 76 / 10%);
  color: var(--el-color-danger);
}
h1 {
  margin: 0;
  font-size: 28px;
  letter-spacing: -0.2px;
  line-height: 1.25;
  overflow-wrap: anywhere;
}
.info-destination {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.info-scopes {
  margin-top: 28px;
  font-size: 13px;
}
.info-scopes strong {
  color: var(--el-text-color-primary);
  font-weight: 600;
}
.info-scopes ul {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}
.info-scopes li {
  max-width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
  background: var(--el-fill-color-extra-light);
  color: var(--el-text-color-regular);
  line-height: 1.35;
  overflow-wrap: anywhere;
}
.info-admin-warning {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 20px;
  padding: 12px 14px;
  border: 1px solid rgb(220 150 28 / 42%);
  border-radius: 12px;
  background: rgb(220 150 28 / 12%);
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
}
.info-admin-warning i {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--passport-link-color, #805c00);
}
.info-privacy-note {
  margin: auto 0 0;
  padding-top: 24px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
@media (max-width: 900px) {
  .info-brand {
    margin-bottom: 16px;
  }
  .info-scopes {
    margin-top: 20px;
  }
  .info-privacy-note {
    padding-top: 16px;
  }
}
@media (max-width: 480px) {
  .info-brand {
    margin-bottom: 16px;
  }
  .info-app-header {
    gap: 14px;
  }
  .info-app-logo {
    flex-basis: 56px;
    width: 56px;
    height: 56px;
    border-radius: 14px;
  }
  h1 {
    font-size: 22px;
  }
}
</style>
