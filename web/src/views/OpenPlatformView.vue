<template>
  <div class="open-platform-page">
    <header class="platform-hero">
      <div class="hero-copy">
        <div class="hero-eyebrow">
          <span class="hero-eyebrow-dot" />{{ t('ui.openPlatform.message001') }}
        </div>
        <h1 class="page-title">{{ t('ui.mainLayout.message016') }}</h1>
        <p class="page-subtitle">{{ t('ui.openPlatform.message002') }}</p>
      </div>
      <div class="hero-hint">
        <span class="hero-hint-label">{{ t('ui.openPlatform.message003') }}</span>
        <span>{{ t('ui.openPlatform.message004') }}</span
        ><span class="step-divider">›</span> <span>{{ t('ui.openPlatform.message005') }}</span
        ><span class="step-divider">›</span>
        <span>{{ t('ui.openPlatform.message006') }}</span>
      </div>
    </header>

    <el-row :gutter="16" class="summary-grid">
      <el-col :xs="24" :md="8">
        <el-card class="card summary-card" shadow="never">
          <div class="summary-label">{{ t('ui.openPlatform.message007') }}</div>
          <div class="summary-value">
            <el-tag :type="verificationTagType" effect="light" size="large">
              {{ verificationLabel }}
            </el-tag>
          </div>
          <div class="summary-desc">{{ t('ui.openPlatform.message008') }}</div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card class="card summary-card" shadow="never">
          <div class="summary-label">{{ t('ui.openPlatform.message009') }}</div>
          <div class="summary-count">
            <span class="summary-number">{{ oauthOverview?.currentCount ?? '—' }}</span>
            <span v-if="oauthOverview" class="summary-total">/ {{ oauthOverview.maxApps }}</span>
          </div>
          <div class="summary-desc">{{ t('ui.openPlatform.message010') }}</div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card class="card summary-card" shadow="never">
          <div class="summary-label">{{ t('ui.openPlatform.message011') }}</div>
          <div class="summary-count">
            <span class="summary-number">{{ ssoOverview?.currentCount ?? '—' }}</span>
            <span v-if="ssoOverview" class="summary-total">/ {{ ssoOverview.maxClients }}</span>
          </div>
          <div class="summary-desc">{{ t('ui.openPlatform.message012') }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert
      v-if="oauthOverview && !oauthOverview.verified"
      type="warning"
      :closable="false"
      show-icon
      class="notice-banner"
      :title="t('ui.openPlatform.message013')"
      :description="t('ui.openPlatform.message014')"
    />

    <el-alert
      v-if="ssoOverview && !ssoOverview.admin"
      type="info"
      :closable="false"
      show-icon
      class="notice-banner"
      :title="t('ui.openPlatform.message015')"
      :description="t('ui.openPlatform.message016')"
    />

    <el-card class="card app-list-card" shadow="never">
      <template #header>
        <div class="section-header">
          <div class="section-heading">
            <div class="section-title">{{ t('ui.openPlatform.message009') }}</div>
            <div class="section-subtitle">{{ t('ui.openPlatform.message017') }}</div>
          </div>
          <div class="section-actions">
            <a
              class="docs-link"
              :href="documentUrl('auth/oauth')"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ t('ui.openPlatform.message018') }} <span aria-hidden="true">↗</span>
            </a>
            <el-button text @click="loadOAuthApps" :loading="oauthLoading">{{
              t('ui.devices.message002')
            }}</el-button>
            <el-button
              class="primary-btn"
              type="primary"
              :disabled="!oauthOverview?.canCreate"
              @click="openOAuthCreateDialog"
            >
              {{ t('ui.openPlatform.message019') }}
            </el-button>
          </div>
        </div>
      </template>

      <el-skeleton v-if="oauthLoading" :rows="5" animated />
      <el-empty
        v-else-if="!oauthOverview?.apps.length"
        :description="t('ui.openPlatform.message020')"
      />

      <div v-else class="app-list">
        <div v-for="app in oauthOverview.apps" :key="app.appId" class="app-item app-item--compact">
          <div
            class="app-title-wrap"
            role="button"
            tabindex="0"
            @click="openOAuthDetailDialog(app.appId)"
            @keydown.enter.prevent="openOAuthDetailDialog(app.appId)"
          >
            <el-avatar
              shape="square"
              :size="46"
              :src="app.logoUrl || ''"
              :class="['app-logo', { 'app-logo--image': !!app.logoUrl }]"
            >
              {{ app.appName.slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div class="app-title-body">
              <div class="app-name">{{ app.appName }}</div>
              <div class="app-id">AppID: {{ app.appId }}</div>
            </div>
          </div>

          <div class="app-item-actions">
            <el-button size="small" @click="openOAuthDetailDialog(app.appId)">{{
              t('ui.openPlatform.message021')
            }}</el-button>
            <el-popconfirm
              :title="t('ui.openPlatform.message022')"
              :description="t('ui.openPlatform.message023')"
              :confirm-button-text="t('ui.loginOptions.message020')"
              :cancel-button-text="t('ui.addPasskey.message009')"
              @confirm="handleOAuthDelete(app.appId)"
            >
              <template #reference>
                <el-button size="small" type="danger" plain>{{
                  t('ui.loginOptions.message020')
                }}</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="card app-list-card" shadow="never">
      <template #header>
        <div class="section-header">
          <div class="section-heading">
            <div class="section-title">{{ t('ui.openPlatform.message024') }}</div>
            <div class="section-subtitle">{{ t('ui.openPlatform.message025') }}</div>
          </div>
          <div class="section-actions">
            <a
              class="docs-link"
              :href="documentUrl('auth/sso')"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ t('ui.openPlatform.message026') }} <span aria-hidden="true">↗</span>
            </a>
            <el-button text @click="loadSSOClients" :loading="ssoLoading">{{
              t('ui.devices.message002')
            }}</el-button>
            <el-button
              class="primary-btn"
              type="primary"
              :disabled="!ssoOverview?.canCreate"
              @click="openSSOCreateDialog"
            >
              {{ t('ui.openPlatform.message027') }}
            </el-button>
          </div>
        </div>
      </template>

      <el-skeleton v-if="ssoLoading" :rows="5" animated />
      <el-empty
        v-else-if="ssoOverview?.admin && !ssoOverview.clients.length"
        :description="t('ui.openPlatform.message028')"
      />
      <el-empty
        v-else-if="ssoOverview && !ssoOverview.admin"
        :description="t('ui.openPlatform.message029')"
      />

      <div v-else class="app-list">
        <div
          v-for="client in ssoOverview?.clients"
          :key="client.clientId"
          class="app-item app-item--compact"
        >
          <div
            class="app-title-wrap"
            role="button"
            tabindex="0"
            @click="openSSODetailDialog(client.clientId)"
            @keydown.enter.prevent="openSSODetailDialog(client.clientId)"
          >
            <el-avatar
              shape="square"
              :size="46"
              :src="client.logoUrl || ''"
              :class="['app-logo', { 'app-logo--image': !!client.logoUrl }]"
            >
              {{ client.clientName.slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div class="app-title-body">
              <div class="app-name">{{ client.clientName }}</div>
              <div class="app-id">ClientID: {{ client.clientId }}</div>
            </div>
          </div>

          <div class="app-item-actions">
            <el-button size="small" @click="openSSODetailDialog(client.clientId)">{{
              t('ui.openPlatform.message021')
            }}</el-button>
            <el-popconfirm
              :title="t('ui.openPlatform.message030')"
              :description="t('ui.openPlatform.message031')"
              :confirm-button-text="t('ui.loginOptions.message020')"
              :cancel-button-text="t('ui.addPasskey.message009')"
              @confirm="handleSSODelete(client.clientId)"
            >
              <template #reference>
                <el-button size="small" type="danger" plain>{{
                  t('ui.loginOptions.message020')
                }}</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog
      v-model="oauthDetailDialogVisible"
      class="app-dialog detail-dialog"
      :title="t('ui.openPlatform.message032')"
      width="760px"
      @closed="resetOAuthDetailDialog"
    >
      <div v-if="oauthDetailApp" class="detail-shell">
        <div class="detail-head">
          <div class="detail-title">
            <el-avatar
              shape="square"
              :size="56"
              :src="oauthDetailApp.logoUrl || ''"
              :class="['app-logo', 'detail-logo', { 'app-logo--image': !!oauthDetailApp.logoUrl }]"
            >
              {{ oauthDetailApp.appName.slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div>
              <div class="detail-name">{{ oauthDetailApp.appName }}</div>
              <div class="detail-subrow">
                <span class="detail-mono">AppID: {{ oauthDetailApp.appId }}</span>
                <el-button text size="small" @click="copyText(oauthDetailApp.appId)">{{
                  t('ui.accountRecoveryCard.message012')
                }}</el-button>
              </div>
            </div>
          </div>
          <div class="detail-actions">
            <el-button size="small" @click="triggerLogoSelect('oauth', oauthDetailApp.appId)">{{
              t('ui.openPlatform.message033')
            }}</el-button>
            <el-button size="small" type="primary" @click="openOAuthEditDialog(oauthDetailApp)">{{
              t('ui.openPlatform.message034')
            }}</el-button>
          </div>
        </div>

        <el-descriptions class="detail-desc" :column="1" border>
          <el-descriptions-item :label="t('ui.openPlatform.message035')">
            <div class="detail-stack">
              <div
                v-for="uri in oauthRedirectUris(oauthDetailApp.redirectUri)"
                :key="uri"
                class="detail-code-row"
              >
                <code class="detail-code">{{ uri }}</code>
                <el-button text size="small" @click="copyText(uri)">{{
                  t('ui.accountRecoveryCard.message012')
                }}</el-button>
              </div>
            </div>
          </el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.message036')">{{
            oauthDetailApp.contactInfo
          }}</el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.message037')">
            <div class="meta-scopes">
              <el-tag v-if="!oauthDetailApp.scopes.length" size="small" effect="plain">{{
                t('ui.openPlatform.message038')
              }}</el-tag>
              <el-tag
                v-for="scope in oauthDetailApp.scopes"
                :key="scope"
                size="small"
                effect="plain"
              >
                {{ oauthScopeLabel(scope) }}
              </el-tag>
            </div>
          </el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.message039')">{{
            formatDateTime(oauthDetailApp.createdAt)
          }}</el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.message040')">{{
            formatDateTime(oauthDetailApp.updatedAt)
          }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-empty v-else :description="t('ui.openPlatform.message041')" />
    </el-dialog>

    <el-dialog
      v-model="ssoDetailDialogVisible"
      class="app-dialog detail-dialog"
      :title="t('ui.openPlatform.message042')"
      width="760px"
      @closed="resetSSODetailDialog"
    >
      <div v-if="ssoDetailClient" class="detail-shell">
        <div class="detail-head">
          <div class="detail-title">
            <el-avatar
              shape="square"
              :size="56"
              :src="ssoDetailClient.logoUrl || ''"
              :class="['app-logo', 'detail-logo', { 'app-logo--image': !!ssoDetailClient.logoUrl }]"
            >
              {{ ssoDetailClient.clientName.slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div>
              <div class="detail-name">{{ ssoDetailClient.clientName }}</div>
              <div class="detail-subrow">
                <span class="detail-mono">ClientID: {{ ssoDetailClient.clientId }}</span>
                <el-button text size="small" @click="copyText(ssoDetailClient.clientId)">{{
                  t('ui.accountRecoveryCard.message012')
                }}</el-button>
              </div>
            </div>
          </div>
          <div class="detail-actions">
            <el-button size="small" @click="triggerLogoSelect('sso', ssoDetailClient.clientId)">{{
              t('ui.openPlatform.message033')
            }}</el-button>
            <el-button size="small" type="primary" @click="openSSOEditDialog(ssoDetailClient)">{{
              t('ui.openPlatform.message034')
            }}</el-button>
          </div>
        </div>

        <el-descriptions class="detail-desc" :column="1" border>
          <el-descriptions-item :label="t('ui.openPlatform.message035')">
            <div class="detail-stack">
              <div v-for="uri in ssoDetailClient.redirectUris" :key="uri" class="detail-code-row">
                <code class="detail-code">{{ uri }}</code>
                <el-button text size="small" @click="copyText(uri)">{{
                  t('ui.accountRecoveryCard.message012')
                }}</el-button>
              </div>
            </div>
          </el-descriptions-item>
          <el-descriptions-item
            v-if="ssoDetailClient.postLogoutRedirectUris.length"
            :label="t('ui.openPlatform.message043')"
          >
            <div class="detail-stack">
              <div
                v-for="uri in ssoDetailClient.postLogoutRedirectUris"
                :key="uri"
                class="detail-code-row"
              >
                <code class="detail-code">{{ uri }}</code>
                <el-button text size="small" @click="copyText(uri)">{{
                  t('ui.accountRecoveryCard.message012')
                }}</el-button>
              </div>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="Audience">
            <div class="detail-stack">
              <div
                v-for="audience in ssoDetailClient.audiences"
                :key="audience"
                class="detail-code-row"
              >
                <code class="detail-code">{{ audience }}</code>
                <el-button text size="small" @click="copyText(audience)">{{
                  t('ui.accountRecoveryCard.message012')
                }}</el-button>
              </div>
            </div>
          </el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.message037')">
            <div class="meta-scopes">
              <el-tag
                v-for="scope in ssoDetailClient.scopes"
                :key="scope"
                size="small"
                effect="plain"
              >
                {{ scope }}
              </el-tag>
              <el-tag v-if="ssoDetailClient.requirePkce" size="small" type="success" effect="plain">
                PKCE Required
              </el-tag>
            </div>
          </el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.message039')">{{
            formatDateTime(ssoDetailClient.createdAt)
          }}</el-descriptions-item>
          <el-descriptions-item :label="t('ui.openPlatform.message040')">{{
            formatDateTime(ssoDetailClient.updatedAt)
          }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-empty v-else :description="t('ui.openPlatform.message041')" />
    </el-dialog>

    <el-dialog
      v-model="oauthCreateDialogVisible"
      class="app-dialog"
      :title="t('ui.openPlatform.message044')"
      width="620px"
    >
      <el-form
        ref="oauthCreateFormRef"
        class="app-dialog-form"
        :model="oauthCreateForm"
        :rules="oauthCreateRules"
        label-position="top"
      >
        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item :label="t('ui.openPlatform.message045')" prop="appName">
              <el-input
                v-model="oauthCreateForm.appName"
                maxlength="100"
                :placeholder="t('ui.openPlatform.message046')"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item :label="t('ui.openPlatform.message036')" prop="contactInfo">
              <el-input
                v-model="oauthCreateForm.contactInfo"
                maxlength="120"
                :placeholder="t('ui.openPlatform.message047')"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item :label="t('ui.openPlatform.message035')" prop="redirectUri">
          <el-input
            v-model="oauthCreateForm.redirectUri"
            type="textarea"
            :rows="3"
            :placeholder="t('ui.openPlatform.message048')"
          />
        </el-form-item>

        <el-form-item :label="t('ui.openPlatform.message049')" prop="scopes">
          <el-checkbox-group v-model="oauthCreateForm.scopes">
            <el-checkbox label="profile">{{ t('ui.openPlatform.message050') }}</el-checkbox>
            <el-checkbox label="email">{{ t('ui.loginOptions.message003') }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="oauthCreateDialogVisible = false">{{
          t('ui.addPasskey.message009')
        }}</el-button>
        <el-button type="primary" :loading="oauthSubmitting" @click="handleOAuthCreate">{{
          t('ui.openPlatform.message005')
        }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="oauthEditDialogVisible"
      class="app-dialog"
      :title="t('ui.openPlatform.message051')"
      width="620px"
    >
      <el-form
        ref="oauthEditFormRef"
        class="app-dialog-form"
        :model="oauthEditForm"
        :rules="oauthEditRules"
        label-position="top"
      >
        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item :label="t('ui.openPlatform.message045')" prop="appName">
              <el-input
                v-model="oauthEditForm.appName"
                maxlength="100"
                :placeholder="t('ui.openPlatform.message046')"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item :label="t('ui.openPlatform.message036')" prop="contactInfo">
              <el-input
                v-model="oauthEditForm.contactInfo"
                maxlength="120"
                :placeholder="t('ui.openPlatform.message052')"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item :label="t('ui.openPlatform.message035')" prop="redirectUri">
          <el-input
            v-model="oauthEditForm.redirectUri"
            type="textarea"
            :rows="3"
            :placeholder="t('ui.openPlatform.message048')"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="oauthEditDialogVisible = false">{{
          t('ui.addPasskey.message009')
        }}</el-button>
        <el-button type="primary" :loading="oauthEditSubmitting" @click="handleOAuthEdit">{{
          t('ui.openPlatform.message053')
        }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="ssoCreateDialogVisible"
      class="app-dialog"
      :title="t('ui.openPlatform.message054')"
      width="720px"
    >
      <el-form
        ref="ssoCreateFormRef"
        class="app-dialog-form"
        :model="ssoCreateForm"
        :rules="ssoRules"
        label-position="top"
      >
        <el-row :gutter="12">
          <el-col :xs="24" :md="16">
            <el-form-item :label="t('ui.openPlatform.message055')" prop="clientName">
              <el-input
                v-model="ssoCreateForm.clientName"
                maxlength="120"
                :placeholder="t('ui.openPlatform.message056')"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="PKCE">
              <el-switch
                v-model="ssoCreateForm.requirePkce"
                inline-prompt
                :active-text="t('ui.openPlatform.message057')"
                :inactive-text="t('ui.accountRecoveryCard.message017')"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item :label="t('ui.openPlatform.message035')" prop="redirectUrisText">
          <el-input
            v-model="ssoCreateForm.redirectUrisText"
            type="textarea"
            :rows="4"
            :placeholder="t('ui.openPlatform.message058')"
          />
        </el-form-item>

        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item label="Audience" prop="audiencesText">
              <el-input
                v-model="ssoCreateForm.audiencesText"
                type="textarea"
                :rows="2"
                :placeholder="t('ui.openPlatform.message059')"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item
              :label="t('ui.openPlatform.message060')"
              prop="postLogoutRedirectUrisText"
            >
              <el-input
                v-model="ssoCreateForm.postLogoutRedirectUrisText"
                type="textarea"
                :rows="2"
                :placeholder="t('ui.openPlatform.message061')"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item :label="t('ui.openPlatform.message049')" prop="scopes">
          <el-checkbox-group v-model="ssoCreateForm.scopes">
            <el-checkbox label="openid">{{ t('ui.openPlatform.message062') }}</el-checkbox>
            <el-checkbox label="profile">{{ t('ui.openPlatform.message050') }}</el-checkbox>
            <el-checkbox label="email">{{ t('ui.loginOptions.message003') }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="ssoCreateDialogVisible = false">{{
          t('ui.addPasskey.message009')
        }}</el-button>
        <el-button type="primary" :loading="ssoSubmitting" @click="handleSSOCreate">{{
          t('ui.openPlatform.message005')
        }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="ssoEditDialogVisible"
      class="app-dialog"
      :title="t('ui.openPlatform.message063')"
      width="720px"
    >
      <el-form
        ref="ssoEditFormRef"
        class="app-dialog-form"
        :model="ssoEditForm"
        :rules="ssoRules"
        label-position="top"
      >
        <el-row :gutter="12">
          <el-col :xs="24" :md="16">
            <el-form-item :label="t('ui.openPlatform.message055')" prop="clientName">
              <el-input
                v-model="ssoEditForm.clientName"
                maxlength="120"
                :placeholder="t('ui.openPlatform.message056')"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="PKCE">
              <el-switch
                v-model="ssoEditForm.requirePkce"
                inline-prompt
                :active-text="t('ui.openPlatform.message057')"
                :inactive-text="t('ui.accountRecoveryCard.message017')"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item :label="t('ui.openPlatform.message035')" prop="redirectUrisText">
          <el-input
            v-model="ssoEditForm.redirectUrisText"
            type="textarea"
            :rows="4"
            :placeholder="t('ui.openPlatform.message058')"
          />
        </el-form-item>

        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item label="Audience" prop="audiencesText">
              <el-input
                v-model="ssoEditForm.audiencesText"
                type="textarea"
                :rows="2"
                :placeholder="t('ui.openPlatform.message064')"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item
              :label="t('ui.openPlatform.message060')"
              prop="postLogoutRedirectUrisText"
            >
              <el-input
                v-model="ssoEditForm.postLogoutRedirectUrisText"
                type="textarea"
                :rows="2"
                :placeholder="t('ui.openPlatform.message061')"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item :label="t('ui.openPlatform.message049')" prop="scopes">
          <el-checkbox-group v-model="ssoEditForm.scopes">
            <el-checkbox label="openid">{{ t('ui.openPlatform.message062') }}</el-checkbox>
            <el-checkbox label="profile">{{ t('ui.openPlatform.message050') }}</el-checkbox>
            <el-checkbox label="email">{{ t('ui.loginOptions.message003') }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="ssoEditDialogVisible = false">{{
          t('ui.addPasskey.message009')
        }}</el-button>
        <el-button type="primary" :loading="ssoEditSubmitting" @click="handleSSOEdit">{{
          t('ui.openPlatform.message053')
        }}</el-button>
      </template>
    </el-dialog>

    <input
      ref="logoFileInput"
      class="file-input"
      type="file"
      accept="image/*"
      @change="handleLogoChange"
    />

    <el-dialog
      v-model="logoCropDialogVisible"
      class="app-dialog"
      :title="t('ui.openPlatform.message065')"
      width="520px"
      destroy-on-close
      @close="resetLogoCropDialog"
    >
      <div class="avatar-crop-wrapper">
        <div class="crop-stage" @mousedown="startDragImage" @touchstart="startDragImage">
          <img
            v-if="cropImageUrl"
            :src="cropImageUrl"
            class="crop-image"
            :style="cropImageStyle"
            draggable="false"
          />
          <div class="crop-mask crop-mask-top" />
          <div class="crop-mask crop-mask-bottom" />
          <div class="crop-mask crop-mask-left" />
          <div class="crop-mask crop-mask-right" />
          <div class="crop-frame" />
        </div>
        <div class="crop-slider-row">
          <span>{{ t('ui.openPlatform.message066') }}</span>
          <el-slider
            v-model="cropScale"
            :min="cropMinScale"
            :max="cropMaxScale"
            :step="0.01"
            @input="handleCropScaleChange"
          />
        </div>
      </div>

      <template #footer>
        <el-button @click="logoCropDialogVisible = false">{{
          t('ui.addPasskey.message009')
        }}</el-button>
        <el-button type="primary" :loading="logoUploading" @click="confirmLogoCrop">{{
          t('ui.openPlatform.message067')
        }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="secretDialogVisible"
      class="app-dialog"
      :title="secretDialogTitle"
      width="560px"
    >
      <div v-if="oauthCreatedApp" class="secret-panel">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          :title="t('ui.openPlatform.message068')"
        />
        <div class="secret-item">
          <span class="secret-label">AppID</span>
          <div class="secret-value">
            <code>{{ oauthCreatedApp.appId }}</code>
            <el-button text @click="copyText(oauthCreatedApp.appId)">{{
              t('ui.accountRecoveryCard.message012')
            }}</el-button>
          </div>
        </div>
        <div class="secret-item">
          <span class="secret-label">AppSecret</span>
          <div class="secret-value">
            <code>{{ oauthCreatedApp.appSecret }}</code>
            <el-button text @click="copyText(oauthCreatedApp.appSecret)">{{
              t('ui.accountRecoveryCard.message012')
            }}</el-button>
          </div>
        </div>
      </div>

      <div v-else-if="ssoCreatedClient" class="secret-panel">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          :title="t('ui.openPlatform.message069')"
        />
        <div class="secret-item">
          <span class="secret-label">ClientID</span>
          <div class="secret-value">
            <code>{{ ssoCreatedClient.clientId }}</code>
            <el-button text @click="copyText(ssoCreatedClient.clientId)">{{
              t('ui.accountRecoveryCard.message012')
            }}</el-button>
          </div>
        </div>
        <div class="secret-item">
          <span class="secret-label">ClientSecret</span>
          <div class="secret-value">
            <code>{{ ssoCreatedClient.clientSecret }}</code>
            <el-button text @click="copyText(ssoCreatedClient.clientSecret)">{{
              t('ui.accountRecoveryCard.message012')
            }}</el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { verificationDisplayType } from '@/utils/userRoles'
import { intlLocale, t, LocalizedError } from '@/i18n'
import { documentUrl } from '@/utils/documentLinks'

import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createOAuth2App,
  deleteOAuth2App,
  getOAuth2Apps,
  uploadOAuth2AppLogo,
  updateOAuth2App,
  type CreateOAuth2AppResponse,
  type OAuth2App,
  type OAuth2AppsOverview,
  type OAuth2Scope,
} from '@/api/oauth2'
import {
  createSSOClient,
  deleteSSOClient,
  getSSOClients,
  uploadSSOClientLogo,
  updateSSOClient,
  type CreateSSOClientResponse,
  type SSOClient,
  type SSOClientsOverview,
  type SSOScope,
} from '@/api/sso'

const oauthLoading = ref(false)
const ssoLoading = ref(false)
const oauthSubmitting = ref(false)
const oauthEditSubmitting = ref(false)
const ssoSubmitting = ref(false)
const ssoEditSubmitting = ref(false)

const oauthCreateDialogVisible = ref(false)
const oauthEditDialogVisible = ref(false)
const ssoCreateDialogVisible = ref(false)
const ssoEditDialogVisible = ref(false)
const oauthDetailDialogVisible = ref(false)
const ssoDetailDialogVisible = ref(false)
const secretDialogVisible = ref(false)
const logoCropDialogVisible = ref(false)
const logoUploading = ref(false)

const oauthOverview = ref<OAuth2AppsOverview | null>(null)
const ssoOverview = ref<SSOClientsOverview | null>(null)
const oauthDetailAppId = ref('')
const ssoDetailClientId = ref('')
const oauthCreatedApp = ref<CreateOAuth2AppResponse | null>(null)
const ssoCreatedClient = ref<CreateSSOClientResponse | null>(null)

const oauthCreateFormRef = ref<FormInstance>()
const oauthEditFormRef = ref<FormInstance>()
const ssoCreateFormRef = ref<FormInstance>()
const ssoEditFormRef = ref<FormInstance>()

const editingOAuthAppId = ref('')
const editingSSOClientId = ref('')
const logoFileInput = ref<HTMLInputElement>()
const logoUploadTargetType = ref<'oauth' | 'sso' | ''>('')
const logoUploadTargetId = ref('')
const selectedLogoFile = ref<File | null>(null)

const CROP_STAGE_SIZE = 320
const CROP_FRAME_SIZE = 220
const CROP_STAGE_CENTER = CROP_STAGE_SIZE / 2
const CROP_FRAME_LEFT = (CROP_STAGE_SIZE - CROP_FRAME_SIZE) / 2
const CROP_FRAME_TOP = (CROP_STAGE_SIZE - CROP_FRAME_SIZE) / 2
const MAX_LOGO_SIZE = 3 * 1024 * 1024
const cropImageUrl = ref('')
const cropImageNaturalWidth = ref(0)
const cropImageNaturalHeight = ref(0)
const cropScale = ref(1)
const cropMinScale = ref(1)
const cropMaxScale = ref(3)
const cropOffsetX = ref(0)
const cropOffsetY = ref(0)
const dragState = reactive({
  active: false,
  startX: 0,
  startY: 0,
  startOffsetX: 0,
  startOffsetY: 0,
})

const oauthCreateForm = reactive<{
  appName: string
  redirectUri: string
  contactInfo: string
  scopes: OAuth2Scope[]
}>({
  appName: '',
  redirectUri: '',
  contactInfo: '',
  scopes: ['profile'],
})

const oauthEditForm = reactive<{
  appName: string
  redirectUri: string
  contactInfo: string
}>({
  appName: '',
  redirectUri: '',
  contactInfo: '',
})

const ssoCreateForm = reactive({
  clientName: '',
  redirectUrisText: '',
  postLogoutRedirectUrisText: '',
  audiencesText: 'ksuser-auth',
  scopes: ['openid', 'profile', 'email'] as SSOScope[],
  requirePkce: true,
})

const ssoEditForm = reactive({
  clientName: '',
  redirectUrisText: '',
  postLogoutRedirectUrisText: '',
  audiencesText: 'ksuser-auth',
  scopes: ['openid', 'profile', 'email'] as SSOScope[],
  requirePkce: true,
})

const normalizeLines = (value: string) =>
  value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)

const normalizeUriTextValues = (value: string) =>
  value
    .split(/[;\n]/)
    .map((item) => item.trim())
    .filter(Boolean)

const normalizeSemicolonValues = (value: string) =>
  value
    .split(';')
    .map((item) => item.trim())
    .filter(Boolean)

const oauthRedirectUris = (value: string) => normalizeSemicolonValues(value || '')

const verificationLabel = computed(() => {
  const type =
    verificationDisplayType(oauthOverview.value || ssoOverview.value)
  if (type === 'admin') return t('ui.mainLayout.message017')
  if (type === 'enterprise') return t('ui.mainLayout.message018')
  if (type === 'personal') return t('ui.mainLayout.message019')
  return t('ui.openPlatform.message070')
})

const verificationTagType = computed(() => {
  const type =
    verificationDisplayType(oauthOverview.value || ssoOverview.value)
  if (type === 'admin') return 'danger'
  if (type === 'enterprise') return 'warning'
  if (type === 'personal') return 'success'
  return 'info'
})

const secretDialogTitle = computed(() => {
  if (oauthCreatedApp.value) return t('ui.openPlatform.message071')
  if (ssoCreatedClient.value) return t('ui.openPlatform.message072')
  return t('ui.openPlatform.message073')
})

const redirectUriValidator = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const values = normalizeSemicolonValues(value || '')
  if (!values.length) {
    callback(new LocalizedError('ui.openPlatform.message074'))
    return
  }
  if (values.join(';').length > 500) {
    callback(new LocalizedError('ui.openPlatform.message075'))
    return
  }

  try {
    values.forEach((raw) => {
      const url = new URL(raw)
      const isHttps = url.protocol === 'https:'
      const isLocalhostHttp = url.protocol === 'http:' && url.hostname === 'localhost'
      if (!isHttps && !isLocalhostHttp) {
        throw new LocalizedError('ui.openPlatform.message076')
      }
      if (url.hash) {
        throw new LocalizedError('ui.openPlatform.message077')
      }
    })
    callback()
  } catch (error) {
    callback(error instanceof Error ? error : new LocalizedError('ui.openPlatform.message078'))
  }
}

const multilineUriValidator = (
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
) => {
  const lines = normalizeUriTextValues(value || '')
  if (!lines.length) {
    callback(new LocalizedError('ui.openPlatform.message079'))
    return
  }

  try {
    lines.forEach((line) => {
      const url = new URL(line)
      const isHttps = url.protocol === 'https:'
      const isLocalhostHttp = url.protocol === 'http:' && url.hostname === 'localhost'
      if (!isHttps && !isLocalhostHttp) {
        throw new LocalizedError('ui.openPlatform.message080')
      }
      if (url.hash) {
        throw new LocalizedError('ui.openPlatform.message081')
      }
    })
    callback()
  } catch (error) {
    callback(error instanceof Error ? error : new LocalizedError('ui.openPlatform.message082'))
  }
}

const audiencesValidator = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!normalizeLines(value || '').length) {
    callback(new LocalizedError('ui.openPlatform.message083'))
    return
  }
  callback()
}

const oauthCreateRules = computed<FormRules>(() => ({
  appName: [
    { required: true, message: t('ui.openPlatform.message084'), trigger: 'blur' },
    { min: 2, max: 100, message: t('ui.openPlatform.message085'), trigger: 'blur' },
  ],
  redirectUri: [{ validator: redirectUriValidator, trigger: 'blur' }],
  contactInfo: [
    { required: true, message: t('ui.openPlatform.message086'), trigger: 'blur' },
    { min: 3, max: 120, message: t('ui.openPlatform.message087'), trigger: 'blur' },
  ],
}))

const oauthEditRules = computed<FormRules>(() => ({
  appName: oauthCreateRules.value.appName,
  redirectUri: oauthCreateRules.value.redirectUri,
  contactInfo: oauthCreateRules.value.contactInfo,
}))

const ssoRules = computed<FormRules>(() => ({
  clientName: [
    { required: true, message: t('ui.openPlatform.message088'), trigger: 'blur' },
    { min: 2, max: 120, message: t('ui.openPlatform.message089'), trigger: 'blur' },
  ],
  redirectUrisText: [{ validator: multilineUriValidator, trigger: 'blur' }],
  postLogoutRedirectUrisText: [
    {
      validator: (_rule, value, callback) => {
        const lines = normalizeLines(value || '')
        if (!lines.length) {
          callback()
          return
        }
        multilineUriValidator(_rule, value, callback)
      },
      trigger: 'blur',
    },
  ],
  audiencesText: [{ validator: audiencesValidator, trigger: 'blur' }],
}))

const oauthScopeLabel = (scope: OAuth2Scope) =>
  scope === 'email' ? t('ui.loginOptions.message003') : t('ui.openPlatform.message050')

const formatDateTime = (value?: string) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(intlLocale.value, { hour12: false })
}

const resetOAuthCreateForm = () => {
  oauthCreateForm.appName = ''
  oauthCreateForm.redirectUri = ''
  oauthCreateForm.contactInfo = ''
  oauthCreateForm.scopes = ['profile']
  oauthCreateFormRef.value?.clearValidate()
}

const resetOAuthEditForm = () => {
  editingOAuthAppId.value = ''
  oauthEditForm.appName = ''
  oauthEditForm.redirectUri = ''
  oauthEditForm.contactInfo = ''
  oauthEditFormRef.value?.clearValidate()
}

const resetSSOCreateForm = () => {
  ssoCreateForm.clientName = ''
  ssoCreateForm.redirectUrisText = ''
  ssoCreateForm.postLogoutRedirectUrisText = ''
  ssoCreateForm.audiencesText = 'ksuser-auth'
  ssoCreateForm.scopes = ['openid', 'profile', 'email']
  ssoCreateForm.requirePkce = true
  ssoCreateFormRef.value?.clearValidate()
}

const resetSSOEditForm = () => {
  editingSSOClientId.value = ''
  ssoEditForm.clientName = ''
  ssoEditForm.redirectUrisText = ''
  ssoEditForm.postLogoutRedirectUrisText = ''
  ssoEditForm.audiencesText = 'ksuser-auth'
  ssoEditForm.scopes = ['openid', 'profile', 'email']
  ssoEditForm.requirePkce = true
  ssoEditFormRef.value?.clearValidate()
}

const loadOAuthApps = async () => {
  oauthLoading.value = true
  try {
    oauthOverview.value = await getOAuth2Apps()
    ensureOAuthDetailExists()
  } finally {
    oauthLoading.value = false
  }
}

const loadSSOClients = async () => {
  ssoLoading.value = true
  try {
    ssoOverview.value = await getSSOClients()
    ensureSSODetailExists()
  } finally {
    ssoLoading.value = false
  }
}

const oauthDetailApp = computed(() => {
  const appId = oauthDetailAppId.value
  if (!appId) return null
  return oauthOverview.value?.apps.find((app) => app.appId === appId) ?? null
})

const ssoDetailClient = computed(() => {
  const clientId = ssoDetailClientId.value
  if (!clientId) return null
  return ssoOverview.value?.clients.find((client) => client.clientId === clientId) ?? null
})

const ensureOAuthDetailExists = () => {
  if (!oauthDetailDialogVisible.value || !oauthDetailAppId.value) return
  if (!oauthDetailApp.value) {
    oauthDetailDialogVisible.value = false
    oauthDetailAppId.value = ''
  }
}

const ensureSSODetailExists = () => {
  if (!ssoDetailDialogVisible.value || !ssoDetailClientId.value) return
  if (!ssoDetailClient.value) {
    ssoDetailDialogVisible.value = false
    ssoDetailClientId.value = ''
  }
}

const openOAuthDetailDialog = (appId: string) => {
  oauthDetailAppId.value = appId
  oauthDetailDialogVisible.value = true
}

const openSSODetailDialog = (clientId: string) => {
  ssoDetailClientId.value = clientId
  ssoDetailDialogVisible.value = true
}

const resetOAuthDetailDialog = () => {
  oauthDetailAppId.value = ''
}

const resetSSODetailDialog = () => {
  ssoDetailClientId.value = ''
}

const openOAuthCreateDialog = () => {
  if (!oauthOverview.value?.canCreate) {
    ElMessage.warning(t('ui.openPlatform.message090'))
    return
  }
  resetOAuthCreateForm()
  oauthCreateDialogVisible.value = true
}

const openOAuthEditDialog = (app: OAuth2App) => {
  editingOAuthAppId.value = app.appId
  oauthEditForm.appName = app.appName
  oauthEditForm.redirectUri = app.redirectUri
  oauthEditForm.contactInfo = app.contactInfo
  oauthEditDialogVisible.value = true
}

const openSSOCreateDialog = () => {
  if (!ssoOverview.value?.canCreate) {
    ElMessage.warning(t('ui.openPlatform.message091'))
    return
  }
  resetSSOCreateForm()
  ssoCreateDialogVisible.value = true
}

const openSSOEditDialog = (client: SSOClient) => {
  editingSSOClientId.value = client.clientId
  ssoEditForm.clientName = client.clientName
  ssoEditForm.redirectUrisText = client.redirectUris.join(';')
  ssoEditForm.postLogoutRedirectUrisText = client.postLogoutRedirectUris.join(';')
  ssoEditForm.audiencesText = client.audiences.join('\n')
  ssoEditForm.scopes = [...client.scopes]
  ssoEditForm.requirePkce = client.requirePkce
  ssoEditDialogVisible.value = true
}

const handleOAuthCreate = async () => {
  await oauthCreateFormRef.value?.validate()
  oauthSubmitting.value = true
  try {
    oauthCreatedApp.value = await createOAuth2App({
      appName: oauthCreateForm.appName.trim(),
      redirectUri: normalizeSemicolonValues(oauthCreateForm.redirectUri).join(';'),
      contactInfo: oauthCreateForm.contactInfo.trim(),
      scopes: [...oauthCreateForm.scopes],
    })
    ssoCreatedClient.value = null
    oauthCreateDialogVisible.value = false
    secretDialogVisible.value = true
    ElMessage.success(t('ui.openPlatform.message092'))
    await loadOAuthApps()
  } finally {
    oauthSubmitting.value = false
  }
}

const handleOAuthEdit = async () => {
  await oauthEditFormRef.value?.validate()
  if (!editingOAuthAppId.value) {
    ElMessage.error(t('ui.openPlatform.message093'))
    return
  }

  oauthEditSubmitting.value = true
  try {
    await updateOAuth2App(editingOAuthAppId.value, {
      appName: oauthEditForm.appName.trim(),
      redirectUri: normalizeSemicolonValues(oauthEditForm.redirectUri).join(';'),
      contactInfo: oauthEditForm.contactInfo.trim(),
    })
    oauthEditDialogVisible.value = false
    resetOAuthEditForm()
    ElMessage.success(t('ui.openPlatform.message094'))
    await loadOAuthApps()
  } finally {
    oauthEditSubmitting.value = false
  }
}

const handleOAuthDelete = async (appId: string) => {
  await deleteOAuth2App(appId)
  ElMessage.success(t('ui.openPlatform.message095'))
  await loadOAuthApps()
}

const handleSSOCreate = async () => {
  await ssoCreateFormRef.value?.validate()
  ssoSubmitting.value = true
  try {
    ssoCreatedClient.value = await createSSOClient({
      clientName: ssoCreateForm.clientName.trim(),
      redirectUris: normalizeUriTextValues(ssoCreateForm.redirectUrisText),
      postLogoutRedirectUris: normalizeUriTextValues(ssoCreateForm.postLogoutRedirectUrisText),
      scopes: [...ssoCreateForm.scopes],
      audiences: normalizeLines(ssoCreateForm.audiencesText),
      requirePkce: ssoCreateForm.requirePkce,
    })
    oauthCreatedApp.value = null
    ssoCreateDialogVisible.value = false
    secretDialogVisible.value = true
    ElMessage.success(t('ui.openPlatform.message096'))
    await loadSSOClients()
  } finally {
    ssoSubmitting.value = false
  }
}

const handleSSOEdit = async () => {
  await ssoEditFormRef.value?.validate()
  if (!editingSSOClientId.value) {
    ElMessage.error(t('ui.openPlatform.message097'))
    return
  }

  ssoEditSubmitting.value = true
  try {
    await updateSSOClient(editingSSOClientId.value, {
      clientName: ssoEditForm.clientName.trim(),
      redirectUris: normalizeUriTextValues(ssoEditForm.redirectUrisText),
      postLogoutRedirectUris: normalizeUriTextValues(ssoEditForm.postLogoutRedirectUrisText),
      scopes: [...ssoEditForm.scopes],
      audiences: normalizeLines(ssoEditForm.audiencesText),
      requirePkce: ssoEditForm.requirePkce,
    })
    ssoEditDialogVisible.value = false
    resetSSOEditForm()
    ElMessage.success(t('ui.openPlatform.message098'))
    await loadSSOClients()
  } finally {
    ssoEditSubmitting.value = false
  }
}

const handleSSODelete = async (clientId: string) => {
  await deleteSSOClient(clientId)
  ElMessage.success(t('ui.openPlatform.message099'))
  await loadSSOClients()
}

watch(oauthDetailDialogVisible, (visible) => {
  if (visible) ensureOAuthDetailExists()
})

watch(ssoDetailDialogVisible, (visible) => {
  if (visible) ensureSSODetailExists()
})

const triggerLogoSelect = (targetType: 'oauth' | 'sso', targetId: string) => {
  logoUploadTargetType.value = targetType
  logoUploadTargetId.value = targetId
  logoFileInput.value?.click()
}

const handleLogoChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.error(t('ui.openPlatform.message100'))
    target.value = ''
    return
  }
  if (file.size > MAX_LOGO_SIZE) {
    ElMessage.error(t('ui.openPlatform.message101'))
    target.value = ''
    return
  }

  selectedLogoFile.value = file
  openLogoCropDialog(file)
}

const openLogoCropDialog = (file: File) => {
  if (cropImageUrl.value) {
    URL.revokeObjectURL(cropImageUrl.value)
  }
  const objectUrl = URL.createObjectURL(file)
  const image = new Image()
  image.onload = () => {
    cropImageNaturalWidth.value = image.naturalWidth
    cropImageNaturalHeight.value = image.naturalHeight
    initializeCropState()
    cropImageUrl.value = objectUrl
    logoCropDialogVisible.value = true
  }
  image.onerror = () => {
    URL.revokeObjectURL(objectUrl)
    ElMessage.error(t('ui.openPlatform.message102'))
  }
  image.src = objectUrl
}

const initializeCropState = () => {
  if (!cropImageNaturalWidth.value || !cropImageNaturalHeight.value) return
  const minScaleByFrame = Math.max(
    CROP_FRAME_SIZE / cropImageNaturalWidth.value,
    CROP_FRAME_SIZE / cropImageNaturalHeight.value,
  )
  cropMinScale.value = minScaleByFrame
  cropMaxScale.value = Math.max(minScaleByFrame * 4, minScaleByFrame + 1)
  cropScale.value = minScaleByFrame
  cropOffsetX.value = CROP_STAGE_CENTER - (cropImageNaturalWidth.value * cropScale.value) / 2
  cropOffsetY.value = CROP_STAGE_CENTER - (cropImageNaturalHeight.value * cropScale.value) / 2
  clampCropOffset()
}

const clampCropOffset = () => {
  if (!cropImageNaturalWidth.value || !cropImageNaturalHeight.value) return
  const displayWidth = cropImageNaturalWidth.value * cropScale.value
  const displayHeight = cropImageNaturalHeight.value * cropScale.value
  const minX = CROP_FRAME_LEFT + CROP_FRAME_SIZE - displayWidth
  const maxX = CROP_FRAME_LEFT
  const minY = CROP_FRAME_TOP + CROP_FRAME_SIZE - displayHeight
  const maxY = CROP_FRAME_TOP
  cropOffsetX.value = Math.min(Math.max(cropOffsetX.value, minX), maxX)
  cropOffsetY.value = Math.min(Math.max(cropOffsetY.value, minY), maxY)
}

const startDragImage = (event: MouseEvent | TouchEvent) => {
  if (!cropImageUrl.value) return
  dragState.active = true
  const point = getEventPoint(event)
  dragState.startX = point.x
  dragState.startY = point.y
  dragState.startOffsetX = cropOffsetX.value
  dragState.startOffsetY = cropOffsetY.value

  window.addEventListener('mousemove', handleDragging)
  window.addEventListener('mouseup', stopDragImage)
  window.addEventListener('touchmove', handleDragging, { passive: false })
  window.addEventListener('touchend', stopDragImage)
}

const handleDragging = (event: MouseEvent | TouchEvent) => {
  if (!dragState.active) return
  if (event instanceof TouchEvent) {
    event.preventDefault()
  }
  const point = getEventPoint(event)
  cropOffsetX.value = dragState.startOffsetX + (point.x - dragState.startX)
  cropOffsetY.value = dragState.startOffsetY + (point.y - dragState.startY)
  clampCropOffset()
}

const stopDragImage = () => {
  dragState.active = false
  window.removeEventListener('mousemove', handleDragging)
  window.removeEventListener('mouseup', stopDragImage)
  window.removeEventListener('touchmove', handleDragging)
  window.removeEventListener('touchend', stopDragImage)
}

const getEventPoint = (event: MouseEvent | TouchEvent) => {
  if (event instanceof TouchEvent) {
    const touch = event.touches[0] || event.changedTouches[0]
    return { x: touch?.clientX || 0, y: touch?.clientY || 0 }
  }
  return { x: event.clientX, y: event.clientY }
}

const handleCropScaleChange = (value: number | number[]) => {
  const nextValue = Array.isArray(value) ? value[0] : value
  if (!nextValue || !cropImageNaturalWidth.value || !cropImageNaturalHeight.value) return
  const prevScale = cropScale.value
  const imagePointX = (CROP_STAGE_CENTER - cropOffsetX.value) / prevScale
  const imagePointY = (CROP_STAGE_CENTER - cropOffsetY.value) / prevScale

  cropScale.value = nextValue
  cropOffsetX.value = CROP_STAGE_CENTER - imagePointX * cropScale.value
  cropOffsetY.value = CROP_STAGE_CENTER - imagePointY * cropScale.value
  clampCropOffset()
}

const exportCroppedLogoBlob = async (): Promise<Blob> => {
  return await new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => {
      const sourceX = (CROP_FRAME_LEFT - cropOffsetX.value) / cropScale.value
      const sourceY = (CROP_FRAME_TOP - cropOffsetY.value) / cropScale.value
      const sourceSize = CROP_FRAME_SIZE / cropScale.value

      const canvas = document.createElement('canvas')
      canvas.width = 512
      canvas.height = 512
      const context = canvas.getContext('2d')
      if (!context) {
        reject(new Error('canvas_context_unavailable'))
        return
      }
      context.drawImage(
        image,
        sourceX,
        sourceY,
        sourceSize,
        sourceSize,
        0,
        0,
        canvas.width,
        canvas.height,
      )
      canvas.toBlob((blob) => {
        if (!blob) {
          reject(new Error('crop_failed'))
          return
        }
        resolve(blob)
      }, 'image/png')
    }
    image.onerror = () => reject(new Error('image_load_failed'))
    image.src = cropImageUrl.value
  })
}

const confirmLogoCrop = async () => {
  if (!cropImageUrl.value || !selectedLogoFile.value) return
  if (!logoUploadTargetType.value || !logoUploadTargetId.value) {
    ElMessage.error(t('ui.openPlatform.message103'))
    return
  }

  try {
    logoUploading.value = true
    const blob = await exportCroppedLogoBlob()
    if (logoUploadTargetType.value === 'oauth') {
      await uploadOAuth2AppLogo(logoUploadTargetId.value, blob)
      await loadOAuthApps()
    } else {
      await uploadSSOClientLogo(logoUploadTargetId.value, blob)
      await loadSSOClients()
    }
    logoCropDialogVisible.value = false
    ElMessage.success(t('ui.openPlatform.message104'))
  } catch {
    ElMessage.error(t('ui.openPlatform.message105'))
  } finally {
    logoUploading.value = false
  }
}

const resetLogoCropDialog = () => {
  stopDragImage()
  logoUploading.value = false
  selectedLogoFile.value = null
  if (cropImageUrl.value) {
    URL.revokeObjectURL(cropImageUrl.value)
  }
  cropImageUrl.value = ''
  cropImageNaturalWidth.value = 0
  cropImageNaturalHeight.value = 0
  logoUploadTargetType.value = ''
  logoUploadTargetId.value = ''
  if (logoFileInput.value) {
    logoFileInput.value.value = ''
  }
}

const cropImageStyle = computed(() => ({
  width: `${cropImageNaturalWidth.value * cropScale.value}px`,
  height: `${cropImageNaturalHeight.value * cropScale.value}px`,
  transform: `translate(${cropOffsetX.value}px, ${cropOffsetY.value}px)`,
}))

const copyText = async (value: string) => {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(t('ui.loginOptions.message080'))
  } catch {
    ElMessage.error(t('ui.accountRecoveryCard.message023'))
  }
}

onMounted(() => {
  void Promise.all([loadOAuthApps(), loadSSOClients()])
})

onBeforeUnmount(() => {
  resetLogoCropDialog()
})
</script>

<style scoped>
:deep(.el-dialog.app-dialog) {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.18);
}

:deep(.app-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

:deep(.app-dialog .el-dialog__title) {
  color: var(--el-text-color-primary);
  font-size: 18px;
  font-weight: 650;
}

:deep(.app-dialog .el-dialog__headerbtn) {
  top: 17px;
  right: 18px;
  width: 32px;
  height: 32px;
  border-radius: 9px;
}

:deep(.app-dialog .el-dialog__headerbtn:hover) {
  background: var(--el-fill-color-light);
}

:deep(.app-dialog .el-dialog__body) {
  padding: 22px 24px 8px;
}

:deep(.app-dialog .el-dialog__footer) {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 24px 22px;
}

:deep(.app-dialog .el-dialog__footer .el-button) {
  min-width: 88px;
  height: 38px;
  border-radius: 10px;
  font-weight: 600;
}

.platform-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
  padding: 22px 24px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 18px;
  background:
    radial-gradient(
      circle at 92% 12%,
      color-mix(in srgb, var(--el-color-primary) 10%, transparent),
      transparent 32%
    ),
    var(--el-bg-color-overlay);
}

.hero-copy {
  min-width: 0;
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.hero-eyebrow-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--el-color-primary);
  box-shadow: 0 0 0 4px var(--el-color-primary-light-9);
}

.page-title {
  margin: 0;
  font-size: 28px;
  line-height: 1.25;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.hero-hint {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 9px;
  padding: 11px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  color: var(--el-text-color-regular);
  background: color-mix(in srgb, var(--el-bg-color) 82%, transparent);
  font-size: 12px;
  white-space: nowrap;
}

.hero-hint-label {
  margin-right: 2px;
  color: var(--el-text-color-secondary);
}

.step-divider {
  color: var(--el-color-primary);
  font-size: 17px;
}

.summary-grid,
.notice-banner,
.app-list-card {
  margin-bottom: 16px;
}

.card {
  border-radius: 16px;
  border: 1px solid var(--el-border-color-light);
}

.summary-card {
  min-height: 142px;
  background: var(--el-bg-color-overlay);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.summary-card:hover {
  border-color: var(--el-color-primary-light-7);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
  transform: translateY(-1px);
}

.summary-card :deep(.el-card__body) {
  padding: 16px;
}

.summary-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
}

.summary-value {
  margin-bottom: 12px;
}

.summary-count {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
}

.summary-number {
  font-size: 30px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.summary-total {
  color: var(--el-text-color-secondary);
}

.summary-desc {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.section-heading {
  min-width: 0;
}

.section-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px 12px;
  flex-shrink: 0;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.section-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.docs-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 32px;
  padding: 0 9px;
  border-radius: 8px;
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition:
    background-color 160ms ease,
    color 160ms ease;
}

.docs-link:hover {
  color: var(--el-color-primary-dark-2);
  background: var(--el-color-primary-light-9);
}

.docs-link:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.app-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.app-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color-overlay);
  transition:
    border-color 160ms ease,
    background-color 160ms ease,
    transform 160ms ease;
}

.app-item:hover {
  border-color: var(--el-border-color);
  background: var(--el-fill-color-light);
}

.app-title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.app-title-wrap[role='button'] {
  cursor: pointer;
  user-select: none;
}

.app-title-wrap[role='button']:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
  border-radius: 12px;
}

.app-title-body {
  min-width: 0;
}

.app-logo {
  flex-shrink: 0;
  background: linear-gradient(140deg, #1f9d6c 0%, #2fbf7d 100%);
  color: #fff;
  font-weight: 700;
  border-radius: 12px;
  overflow: hidden;
}

.app-logo--image {
  background: transparent;
  color: var(--el-text-color-primary);
  border: 1px solid var(--el-border-color-lighter);
}

.app-logo :deep(img) {
  object-fit: contain;
  background: transparent;
}

.app-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-id {
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.app-item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.detail-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  background: var(--el-fill-color-extra-light);
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.detail-logo {
  border-radius: 14px;
}

.detail-name {
  font-size: 18px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.detail-subrow {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.detail-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.detail-desc :deep(.el-descriptions__label) {
  width: 120px;
}

.detail-desc :deep(.el-descriptions__table) {
  border-radius: 12px;
  overflow: hidden;
}

.detail-desc :deep(.el-descriptions__label.el-descriptions__cell) {
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.detail-desc :deep(.el-descriptions__content.el-descriptions__cell) {
  color: var(--el-text-color-primary);
  line-height: 1.65;
}

.detail-code-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.detail-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.detail-stack {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.app-dialog :deep(.el-dialog__body) {
  padding: 22px 24px 8px;
}

.app-dialog :deep(.el-dialog__footer) {
  padding: 12px 24px 22px;
}

.app-dialog-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.app-dialog-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.app-dialog-form :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 10px;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
  transition: box-shadow 0.2s ease;
}

.app-dialog-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-color-primary-light-5) inset;
}

.app-dialog-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px var(--el-color-primary) inset,
    0 0 0 3px var(--el-color-primary-light-9);
}

.app-dialog-form :deep(.el-textarea__inner) {
  border-radius: 10px;
  padding: 11px 12px;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}

.app-dialog-form :deep(.el-textarea__inner:focus) {
  box-shadow:
    0 0 0 1px var(--el-color-primary) inset,
    0 0 0 3px var(--el-color-primary-light-9);
}

.app-dialog-form :deep(.el-checkbox-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
}

.app-dialog-form :deep(.el-switch) {
  margin-top: 4px;
}

.app-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 18px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-item-full {
  grid-column: 1 / -1;
}

.meta-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.meta-value,
.stack-code {
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.mono,
.stack-code,
.secret-value code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.meta-scopes,
.meta-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.stack-code {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
}

.secret-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.secret-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.secret-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.secret-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--el-fill-color-extra-light);
}

.file-input {
  display: none;
}

.avatar-crop-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.crop-stage {
  position: relative;
  width: 320px;
  height: 320px;
  margin: 0 auto;
  overflow: hidden;
  border-radius: 16px;
  background: #101418;
  touch-action: none;
  cursor: grab;
  user-select: none;
}

.crop-image {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: top left;
}

.crop-mask {
  position: absolute;
  background: rgba(0, 0, 0, 0.45);
  pointer-events: none;
}

.crop-mask-top {
  top: 0;
  left: 0;
  width: 100%;
  height: 50px;
}

.crop-mask-bottom {
  bottom: 0;
  left: 0;
  width: 100%;
  height: 50px;
}

.crop-mask-left {
  top: 50px;
  left: 0;
  width: 50px;
  height: 220px;
}

.crop-mask-right {
  top: 50px;
  right: 0;
  width: 50px;
  height: 220px;
}

.crop-frame {
  position: absolute;
  top: 50px;
  left: 50px;
  width: 220px;
  height: 220px;
  border: 2px solid rgba(255, 255, 255, 0.88);
  border-radius: 20px;
  pointer-events: none;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.3) inset;
}

.crop-slider-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 4px;
  color: var(--el-text-color-secondary);
}

.crop-slider-row :deep(.el-slider) {
  flex: 1;
}

@media (max-width: 900px) {
  .app-meta-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .platform-hero {
    align-items: stretch;
    flex-direction: column;
    gap: 16px;
    padding: 20px;
  }

  .hero-hint {
    align-self: flex-start;
    flex-wrap: wrap;
    white-space: normal;
  }

  .section-header,
  .app-item,
  .detail-head,
  .secret-value {
    flex-direction: column;
    align-items: stretch;
  }

  .section-actions {
    justify-content: flex-start;
  }

  .section-heading,
  .section-actions {
    width: 100%;
  }

  .app-item-actions,
  .detail-actions {
    justify-content: flex-start;
  }

  :deep(.app-dialog .el-dialog__header) {
    padding: 18px 18px 14px;
  }

  :deep(.app-dialog .el-dialog__body) {
    padding: 18px 18px 6px;
  }

  :deep(.app-dialog .el-dialog__footer) {
    padding: 10px 18px 18px;
  }

  .detail-head {
    align-items: stretch;
  }

  .detail-actions {
    display: flex;
  }

  .page-title {
    font-size: 25px;
  }
}
</style>
