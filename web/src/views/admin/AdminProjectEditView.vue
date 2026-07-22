<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminClient, type AdminProjectInput } from '../../features/admin/admin-client'
import type { AdminProject } from '../../features/admin/admin-schema'
import { projectFormSchema, type ProjectForm } from '../../features/admin/project/project-form-schema'
import { useProjectDraftStore, type ProjectDraftFields } from '../../features/admin/project/project-draft-store'
import { createAdminFormValidation, focusAdminField } from '../../features/admin/admin-form-validation'
import AdminFormField from '../../features/admin/components/AdminFormField.vue'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import MarkdownEditor from '../../features/admin/components/MarkdownEditor.vue'
import { useAdminOperationFeedback } from '../../features/admin/admin-operation-feedback'
import { confirmAdminAction } from '../../features/admin/admin-confirmation'

const props = defineProps<{ adminClient: AdminClient; id?: string }>()
const feedback = useAdminOperationFeedback()
const router = useRouter()
const drafts = useProjectDraftStore()
const isNew = computed(() => !props.id)
const initialized = ref(false); const dirty = ref(false); const saving = ref(false)
const stale = ref(false); const errorMessage = ref<string>(); const scheduleValue = ref('')
const currentStatus = ref<AdminProject['status']>()
const form = reactive<ProjectForm>({ slug:'',title:'',summary:'',descriptionMarkdown:'',projectUrl:null,
  repositoryUrl:null,featured:false,sortOrder:0,version:null,publishMode:'draft',publishAt:null })
const validation=createAdminFormValidation(projectFormSchema)
const fieldOrder=['title','slug','sortOrder','summary','projectUrl','repositoryUrl','descriptionMarkdown','publishAt']
function publishAtValue(){if(form.publishMode!=='scheduled'||!scheduleValue.value)return null
  const value=new Date(scheduleValue.value);return Number.isNaN(value.valueOf())?'invalid':value.toISOString()}
function validationInput(){return{...form,publishAt:publishAtValue()}}
const project = useQuery({ queryKey: computed(() => ['admin','projects',props.id ?? 'new']),
  queryFn: () => props.adminClient.getProject(props.id ?? ''), enabled: computed(() => !isNew.value), retry:false })

const fields = (): ProjectDraftFields => ({ slug:form.slug,title:form.title,summary:form.summary,
  descriptionMarkdown:form.descriptionMarkdown,projectUrl:form.projectUrl,repositoryUrl:form.repositoryUrl,
  featured:form.featured,sortOrder:form.sortOrder })
function assign(value: ProjectDraftFields) { Object.assign(form, value) }
function apply(value: AdminProject) {
  currentStatus.value=value.status
  form.version=value.version; form.publishMode='draft'; form.publishAt=null
  const server={slug:value.slug,title:value.title,summary:value.summary,descriptionMarkdown:value.descriptionMarkdown,
    projectUrl:value.projectUrl,repositoryUrl:value.repositoryUrl,featured:value.featured,sortOrder:value.sortOrder}
  const draft=drafts.load(value.id,value.version); assign(draft ?? server)
  initialized.value=true; dirty.value=!!draft;validation.resetValidation()
}
if(isNew.value){const draft=drafts.load(null,null);if(draft)assign(draft);initialized.value=true;dirty.value=!!draft}
watch(project.data,(value)=>{if(value&&(!initialized.value||!dirty.value))apply(value)},{immediate:true})
watch(form,()=>{if(initialized.value&&!saving.value){dirty.value=true;drafts.save(props.id??null,form.version,fields())}},{deep:true})
const state=computed<'loading'|'error'|'unauthorized'|'forbidden'|'ready'>(()=>{
  if(!isNew.value&&project.isPending.value)return'loading';const error=project.error.value
  if(error instanceof AdminApiError&&error.kind==='unauthorized')return'unauthorized'
  if(error instanceof AdminApiError&&error.kind==='forbidden')return'forbidden'
  return project.isError.value?'error':'ready'
})
function input(parsed:ProjectForm):AdminProjectInput{return{slug:parsed.slug,title:parsed.title,summary:parsed.summary,
  descriptionMarkdown:parsed.descriptionMarkdown,projectUrl:parsed.projectUrl,repositoryUrl:parsed.repositoryUrl,
  featured:parsed.featured,sortOrder:parsed.sortOrder}}
async function save(){if(saving.value)return;stale.value=false;errorMessage.value=undefined
  const result=validation.validateForSubmit(validationInput(),fieldOrder)
  if(!result.success){errorMessage.value='Review the highlighted fields';await focusAdminField(result.firstInvalidField);return}
  saving.value=true
  const operation=isNew.value?'project.create':'project.update'
  try{const parsed=result.data;let saved:AdminProject
    if(isNew.value){saved=(await props.adminClient.createProject(input(parsed))).data;drafts.clear(null);dirty.value=false;await router.replace(`/admin/portfolio/projects/${saved.id}`)}
    else{saved=await props.adminClient.updateProject(props.id??'',{...input(parsed),version:parsed.version??0});drafts.clear(props.id??null)}
    apply(saved);dirty.value=false;feedback.succeeded(operation)
  }catch(error){feedback.failed(operation);if(error instanceof AdminApiError&&error.code==='stale_version')stale.value=true
    else if(error instanceof AdminApiError&&error.fieldErrors){errorMessage.value='Review the highlighted fields';await focusAdminField(validation.applyServerErrors(error.fieldErrors))}
    else if(error instanceof AdminApiError&&error.code==='duplicate_slug')errorMessage.value='Slug is already in use'
    else errorMessage.value='Unable to save project'}finally{saving.value=false}}
async function reload(){const result=await project.refetch();if(result.data){drafts.clear(props.id??null);dirty.value=false;stale.value=false;apply(result.data)}}
async function changeState(action:'publish'|'archive'){if(!props.id||form.version===null||saving.value)return
  if(action==='publish'&&form.publishMode==='scheduled'){validation.touch('publishAt',validationInput())
    if(validation.errorsFor('publishAt').length>0){errorMessage.value='Review the highlighted fields';await focusAdminField('publishAt');return}}
  saving.value=true
  const operation=action==='publish'?'project.publish':'project.archive'
  try{const at=form.publishMode==='scheduled'&&scheduleValue.value?new Date(scheduleValue.value).toISOString():null
    apply(action==='publish'?await props.adminClient.publishProject(props.id,form.version,at):await props.adminClient.archiveProject(props.id,form.version));dirty.value=false;feedback.succeeded(operation)}
  catch{feedback.failed(operation);errorMessage.value=action==='publish'?'Unable to publish project':'Unable to archive project'}
  finally{saving.value=false}}
onBeforeRouteLeave(async()=>{
  if(!dirty.value)return true
  return await confirmAdminAction({title:'放弃未保存更改',message:'确定离开并放弃当前项目修改吗？',confirmButtonText:'放弃更改',cancelButtonText:'继续编辑',type:'warning'})==='confirmed'
})
</script>

<template>
  <section>
    <header class="page-head">
      <div><p>Projects</p><h1>{{ isNew?'New project':'Edit project' }}</h1></div><RouterLink to="/admin/portfolio/projects">
        Back to projects
      </RouterLink>
    </header>
    <AdminPageState
      :state="state"
      @retry="project.refetch()"
    >
      <el-form
        data-testid="save-project"
        class="project-form"
        @submit.prevent="save"
      >
        <div
          v-if="stale"
          class="alert"
        >
          <span>The server version changed. Your input is preserved.</span><el-button
            @click="reload"
          >
            Reload server version
          </el-button>
        </div>
        <p
          v-if="errorMessage"
          class="alert"
        >
          {{ errorMessage }}
        </p>
        <div class="grid">
          <AdminFormField
            class="wide"
            name="title"
            label="Title"
            required
            hint="Required; up to 180 characters"
            :errors="validation.errorsFor('title')"
          >
            <template #default="{controlId,describedBy,invalid}">
              <el-input
                :id="controlId"
                v-model="form.title"
                name="title"
                maxlength="180"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('title',validationInput())"
                @input="validation.change('title',validationInput())"
              />
            </template>
          </AdminFormField>
          <AdminFormField
            name="slug"
            label="Slug"
            required
            hint="3-120 lowercase letters, numbers, and single hyphens"
            :errors="validation.errorsFor('slug')"
          >
            <template #default="{controlId,describedBy,invalid}">
              <el-input
                :id="controlId"
                v-model="form.slug"
                name="slug"
                maxlength="120"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('slug',validationInput())"
                @input="validation.change('slug',validationInput())"
              />
            </template>
          </AdminFormField>
          <AdminFormField
            name="sortOrder"
            label="Sort order"
            required
            hint="Zero or a positive whole number"
            :errors="validation.errorsFor('sortOrder')"
          >
            <template #default="{controlId,describedBy,invalid}">
              <el-input-number
                :id="controlId"
                v-model.number="form.sortOrder"
                name="sortOrder"
                :min="0"
                :step="1"
                :precision="0"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('sortOrder',validationInput())"
                @input="validation.change('sortOrder',validationInput())"
              />
            </template>
          </AdminFormField>
          <AdminFormField
            class="wide"
            name="summary"
            label="Summary"
            required
            hint="Required; up to 500 characters"
            :errors="validation.errorsFor('summary')"
          >
            <template #default="{controlId,describedBy,invalid}">
              <el-input
                :id="controlId"
                v-model="form.summary"
                name="summary"
                type="textarea"
                :rows="3"
                maxlength="500"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('summary',validationInput())"
                @input="validation.change('summary',validationInput())"
              />
            </template>
          </AdminFormField>
          <AdminFormField
            name="projectUrl"
            label="Project URL"
            hint="Optional HTTPS URL without credentials"
            :errors="validation.errorsFor('projectUrl')"
          >
            <template #default="{controlId,describedBy,invalid}">
              <el-input
                :id="controlId"
                v-model="form.projectUrl"
                name="projectUrl"
                maxlength="2048"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('projectUrl',validationInput())"
                @input="validation.change('projectUrl',validationInput())"
              />
            </template>
          </AdminFormField>
          <AdminFormField
            name="repositoryUrl"
            label="Repository URL"
            hint="Optional HTTPS URL without credentials"
            :errors="validation.errorsFor('repositoryUrl')"
          >
            <template #default="{controlId,describedBy,invalid}">
              <el-input
                :id="controlId"
                v-model="form.repositoryUrl"
                name="repositoryUrl"
                maxlength="2048"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('repositoryUrl',validationInput())"
                @input="validation.change('repositoryUrl',validationInput())"
              />
            </template>
          </AdminFormField>
          <el-checkbox v-model="form.featured">
            Featured
          </el-checkbox>
          <AdminFormField
            class="wide"
            name="descriptionMarkdown"
            label="Description"
            hint="Optional; up to 100,000 characters"
            :errors="validation.errorsFor('descriptionMarkdown')"
          >
            <template #default="{controlId,describedBy,invalid}">
              <MarkdownEditor
                :model-value="form.descriptionMarkdown"
                name="descriptionMarkdown"
                :control-id="controlId"
                :described-by="describedBy"
                :invalid="invalid"
                :preview="adminClient.previewProject"
                @update:model-value="form.descriptionMarkdown=$event;validation.change('descriptionMarkdown',validationInput())"
                @blur="validation.touch('descriptionMarkdown',validationInput())"
              />
            </template>
          </AdminFormField>
        </div>
        <fieldset v-if="!isNew&&currentStatus==='DRAFT'">
          <legend>Publication</legend>
          <el-radio-group v-model="form.publishMode">
            <el-radio value="now">
              Now
            </el-radio>
            <el-radio value="scheduled">
              Schedule
            </el-radio>
          </el-radio-group>
          <AdminFormField
            v-if="form.publishMode==='scheduled'"
            name="publishAt"
            label="Publication time"
            required
            hint="Required when scheduling; choose a local date and time"
            :errors="validation.errorsFor('publishAt')"
          >
            <template #default="{ controlId, describedBy, invalid }">
              <el-date-picker
                :id="controlId"
                v-model="scheduleValue"
                name="publishAt"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm"
                format="YYYY-MM-DD HH:mm"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('publishAt', validationInput())"
                @input="validation.change('publishAt', validationInput())"
              />
            </template>
          </AdminFormField><el-button
            type="primary"
            :disabled="saving"
            @click="changeState('publish')"
          >
            Publish
          </el-button>
        </fieldset>
        <div class="actions">
          <el-button
            type="primary"
            native-type="submit"
            :disabled="saving"
          >
            {{ saving?'Saving':'Save' }}
          </el-button><el-button
            v-if="currentStatus==='PUBLISHED'"
            :disabled="saving"
            @click="changeState('archive')"
          >
            Archive
          </el-button>
        </div>
      </el-form>
    </AdminPageState>
  </section>
</template>

<style scoped>
.page-head{display:flex;align-items:end;justify-content:space-between;gap:16px;margin-bottom:24px}.page-head p{margin:0;color:#2f7758;font-size:12px;font-weight:750;text-transform:uppercase}.page-head h1{margin:5px 0 0;font-size:30px;overflow-wrap:anywhere}.project-form{min-width:0}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:15px}.wide{grid-column:1/-1}label{display:grid;gap:5px;min-width:0;color:#59645d;font-size:12px;font-weight:700}input,textarea{width:100%;min-width:0;padding:9px;border:1px solid #aeb8b1;border-radius:4px;overflow-wrap:anywhere}.check{display:flex;align-items:center}.check input,fieldset input{width:auto}.alert{display:flex;justify-content:space-between;gap:12px;padding:11px 13px;border-left:3px solid #a33c34;background:#fff4f2;color:#76261f}fieldset{display:flex;flex-wrap:wrap;gap:12px;align-items:end;margin-top:20px;border:1px solid #d8dfda}.actions{display:flex;gap:8px;margin-top:22px;padding-top:18px;border-top:1px solid #d8dfda}button{min-height:38px;padding:8px 12px;border:1px solid #aeb8b1;border-radius:4px;background:#fff}.primary{background:#202622;color:#fff}@media(max-width:680px){.page-head{align-items:flex-start;flex-direction:column}.grid{grid-template-columns:minmax(0,1fr)}.wide{grid-column:auto}.alert{flex-direction:column}}
</style>
