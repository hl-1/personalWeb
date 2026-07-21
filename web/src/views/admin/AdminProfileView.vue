<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminClient } from '../../features/admin/admin-client'
import type { AdminProfile } from '../../features/admin/admin-schema'
import { parseProfileForm, type ProfileForm } from '../../features/admin/portfolio/portfolio-form-schema'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import MarkdownEditor from '../../features/admin/components/MarkdownEditor.vue'
const props=defineProps<{adminClient:AdminClient}>()
const profile=useQuery({queryKey:['admin','profile'],queryFn:()=>props.adminClient.getProfile(),retry:false})
const form=reactive<ProfileForm>({displayName:'',headline:'',bioMarkdown:'',seoDescription:null,version:null})
const saving=ref(false);const stale=ref(false);const errorMessage=ref<string>();const missing=computed(()=>profile.error.value instanceof AdminApiError&&profile.error.value.code==='not_found')
function applyProfile(value:AdminProfile){Object.assign(form,{displayName:value.displayName,headline:value.headline,bioMarkdown:value.bioMarkdown,seoDescription:value.seoDescription,version:value.version})}
watch(profile.data,(value)=>{if(value)applyProfile(value)},{immediate:true})
const state=computed<'loading'|'error'|'unauthorized'|'forbidden'|'ready'>(()=>{
  if(profile.isPending.value)return'loading';const error=profile.error.value
  if(missing.value)return'ready';if(error instanceof AdminApiError&&error.kind==='unauthorized')return'unauthorized'
  if(error instanceof AdminApiError&&error.kind==='forbidden')return'forbidden';return profile.isError.value?'error':'ready'
})
async function save(){if(saving.value)return;saving.value=true;stale.value=false;errorMessage.value=undefined
  try{const saved=await props.adminClient.upsertProfile(parseProfileForm(form));applyProfile(saved)}
  catch(error){if(error instanceof AdminApiError&&error.code==='stale_version')stale.value=true;else errorMessage.value='Unable to save profile'}finally{saving.value=false}}
async function reload(){const result=await profile.refetch();if(result.data){applyProfile(result.data);stale.value=false}}
</script>
<template>
  <section>
    <header class="head">
      <p>Portfolio</p><h1>{{ missing?'Create profile':'Profile' }}</h1>
    </header>
    <AdminPageState
      :state="state"
      @retry="profile.refetch()"
    >
      <form
        data-testid="save-profile"
        class="form"
        @submit.prevent="save"
      >
        <div
          v-if="stale"
          class="alert"
        >
          The profile changed on the server.<button
            type="button"
            @click="reload"
          >
            Reload server profile
          </button>
        </div>
        <p
          v-if="errorMessage"
          class="alert"
        >
          {{ errorMessage }}
        </p>
        <label><span>Display name</span><input
          v-model="form.displayName"
          name="displayName"
          maxlength="120"
        ></label>
        <label><span>Headline</span><input
          v-model="form.headline"
          name="headline"
          maxlength="180"
        ></label>
        <label><span>Biography</span><MarkdownEditor
          v-model="form.bioMarkdown"
          :preview="adminClient.previewArticle"
        /></label>
        <label><span>SEO description</span><input
          v-model="form.seoDescription"
          name="seoDescription"
          maxlength="160"
        ></label>
        <button
          class="primary"
          type="submit"
          :disabled="saving"
        >
          {{ saving?'Saving':'Save profile' }}
        </button>
      </form>
    </AdminPageState>
  </section>
</template>
<style scoped>.head{margin-bottom:22px}.head p{margin:0;color:#2f7758;font-size:12px;font-weight:750;text-transform:uppercase}.head h1{margin:5px 0 0;font-size:30px}.form{display:grid;gap:15px;max-width:880px}label{display:grid;gap:5px;color:#59645d;font-size:12px;font-weight:700}input{width:100%;padding:9px;border:1px solid #aeb8b1;border-radius:4px}.alert{display:flex;justify-content:space-between;gap:12px;padding:11px;border-left:3px solid #a33c34;background:#fff4f2}.primary,.alert button{min-height:38px;padding:8px 12px;border:1px solid #202622;border-radius:4px}.primary{width:max-content;background:#202622;color:#fff}</style>
