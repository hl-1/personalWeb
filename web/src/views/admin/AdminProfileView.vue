<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminClient } from '../../features/admin/admin-client'
import type { AdminProfile } from '../../features/admin/admin-schema'
import { profileFormSchema, type ProfileForm } from '../../features/admin/portfolio/portfolio-form-schema'
import { createAdminFormValidation, focusAdminField } from '../../features/admin/admin-form-validation'
import AdminFormField from '../../features/admin/components/AdminFormField.vue'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import MarkdownEditor from '../../features/admin/components/MarkdownEditor.vue'
import { useAdminOperationFeedback } from '../../features/admin/admin-operation-feedback'
const props=defineProps<{adminClient:AdminClient}>()
const feedback=useAdminOperationFeedback()
const profile=useQuery({queryKey:['admin','profile'],queryFn:()=>props.adminClient.getProfile(),retry:false})
const form=reactive<ProfileForm>({displayName:'',headline:'',bioMarkdown:'',seoDescription:null,version:null})
const validation=createAdminFormValidation(profileFormSchema)
const fieldOrder=['displayName','headline','bioMarkdown','seoDescription']
const saving=ref(false);const stale=ref(false);const errorMessage=ref<string>();const missing=computed(()=>profile.error.value instanceof AdminApiError&&profile.error.value.code==='not_found')
function validationInput(){return{...form}}
function applyProfile(value:AdminProfile){Object.assign(form,{displayName:value.displayName,headline:value.headline,bioMarkdown:value.bioMarkdown,seoDescription:value.seoDescription,version:value.version});validation.resetValidation()}
watch(profile.data,(value)=>{if(value)applyProfile(value)},{immediate:true})
const state=computed<'loading'|'error'|'unauthorized'|'forbidden'|'ready'>(()=>{
  if(profile.isPending.value)return'loading';const error=profile.error.value
  if(missing.value)return'ready';if(error instanceof AdminApiError&&error.kind==='unauthorized')return'unauthorized'
  if(error instanceof AdminApiError&&error.kind==='forbidden')return'forbidden';return profile.isError.value?'error':'ready'
})
async function save(){if(saving.value)return;stale.value=false;errorMessage.value=undefined
  const result=validation.validateForSubmit(validationInput(),fieldOrder)
  if(!result.success){errorMessage.value='Review the highlighted fields';await focusAdminField(result.firstInvalidField);return}
  saving.value=true
  const operation=result.data.version===null?'profile.create':'profile.update'
  try{const saved=await props.adminClient.upsertProfile(result.data);applyProfile(saved);feedback.succeeded(operation)}
  catch(error){feedback.failed(operation);if(error instanceof AdminApiError&&error.code==='stale_version')stale.value=true
    else if(error instanceof AdminApiError&&error.fieldErrors){errorMessage.value='Review the highlighted fields';await focusAdminField(validation.applyServerErrors(error.fieldErrors))}
    else errorMessage.value='Unable to save profile'}finally{saving.value=false}}
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
      <el-form
        data-testid="save-profile"
        class="form"
        @submit.prevent="save"
      >
        <div
          v-if="stale"
          class="alert"
        >
          The profile changed on the server.<el-button
            @click="reload"
          >
            Reload server profile
          </el-button>
        </div>
        <p
          v-if="errorMessage"
          class="alert"
        >
          {{ errorMessage }}
        </p>
        <AdminFormField
          name="displayName"
          label="Display name"
          required
          hint="Required; up to 120 characters"
          :errors="validation.errorsFor('displayName')"
        >
          <template #default="{controlId,describedBy,invalid}">
            <el-input
              :id="controlId"
              v-model="form.displayName"
              name="displayName"
              maxlength="120"
              :aria-describedby="describedBy"
              :aria-invalid="invalid"
              @blur="validation.touch('displayName',validationInput())"
              @input="validation.change('displayName',validationInput())"
            />
          </template>
        </AdminFormField>
        <AdminFormField
          name="headline"
          label="Headline"
          required
          hint="Required; up to 180 characters"
          :errors="validation.errorsFor('headline')"
        >
          <template #default="{controlId,describedBy,invalid}">
            <el-input
              :id="controlId"
              v-model="form.headline"
              name="headline"
              maxlength="180"
              :aria-describedby="describedBy"
              :aria-invalid="invalid"
              @blur="validation.touch('headline',validationInput())"
              @input="validation.change('headline',validationInput())"
            />
          </template>
        </AdminFormField>
        <AdminFormField
          name="bioMarkdown"
          label="Biography"
          hint="Optional; up to 50,000 characters"
          :errors="validation.errorsFor('bioMarkdown')"
        >
          <template #default="{controlId,describedBy,invalid}">
            <MarkdownEditor
              :model-value="form.bioMarkdown"
              name="bioMarkdown"
              :control-id="controlId"
              :described-by="describedBy"
              :invalid="invalid"
              :preview="adminClient.previewArticle"
              @update:model-value="form.bioMarkdown=$event;validation.change('bioMarkdown',validationInput())"
              @blur="validation.touch('bioMarkdown',validationInput())"
            />
          </template>
        </AdminFormField>
        <AdminFormField
          name="seoDescription"
          label="SEO description"
          hint="Optional; up to 160 characters"
          :errors="validation.errorsFor('seoDescription')"
        >
          <template #default="{controlId,describedBy,invalid}">
            <el-input
              :id="controlId"
              v-model="form.seoDescription"
              name="seoDescription"
              maxlength="160"
              :aria-describedby="describedBy"
              :aria-invalid="invalid"
              @blur="validation.touch('seoDescription',validationInput())"
              @input="validation.change('seoDescription',validationInput())"
            />
          </template>
        </AdminFormField>
        <el-button
          type="primary"
          native-type="submit"
          :disabled="saving"
        >
          {{ saving?'Saving':'Save profile' }}
        </el-button>
      </el-form>
    </AdminPageState>
  </section>
</template>
<style scoped>.head{margin-bottom:22px}.head p{margin:0;color:#2f7758;font-size:12px;font-weight:750;text-transform:uppercase}.head h1{margin:5px 0 0;font-size:30px}.form{display:grid;gap:15px;max-width:880px}label{display:grid;gap:5px;color:#59645d;font-size:12px;font-weight:700}input{width:100%;padding:9px;border:1px solid #aeb8b1;border-radius:4px}.alert{display:flex;justify-content:space-between;gap:12px;padding:11px;border-left:3px solid #a33c34;background:#fff4f2}.primary,.alert button{min-height:38px;padding:8px 12px;border:1px solid #202622;border-radius:4px}.primary{width:max-content;background:#202622;color:#fff}</style>
