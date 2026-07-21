<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminClient, type AdminProjectInput } from '../../features/admin/admin-client'
import type { AdminProject } from '../../features/admin/admin-schema'
import { parseProjectForm, type ProjectForm } from '../../features/admin/project/project-form-schema'
import { useProjectDraftStore, type ProjectDraftFields } from '../../features/admin/project/project-draft-store'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import MarkdownEditor from '../../features/admin/components/MarkdownEditor.vue'

const props = defineProps<{ adminClient: AdminClient; id?: string }>()
const router = useRouter()
const drafts = useProjectDraftStore()
const isNew = computed(() => !props.id)
const initialized = ref(false); const dirty = ref(false); const saving = ref(false)
const stale = ref(false); const errorMessage = ref<string>(); const scheduleValue = ref('')
const currentStatus = ref<AdminProject['status']>()
const form = reactive<ProjectForm>({ slug:'',title:'',summary:'',descriptionMarkdown:'',projectUrl:null,
  repositoryUrl:null,featured:false,sortOrder:0,version:null,publishMode:'draft',publishAt:null })
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
  initialized.value=true; dirty.value=!!draft
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
async function save(){if(saving.value)return;saving.value=true;stale.value=false;errorMessage.value=undefined
  try{const publishAt=form.publishMode==='scheduled'&&scheduleValue.value?new Date(scheduleValue.value).toISOString():null
    const parsed=parseProjectForm({...form,publishAt});let saved:AdminProject
    if(isNew.value){saved=(await props.adminClient.createProject(input(parsed))).data;drafts.clear(null);dirty.value=false;await router.replace(`/admin/portfolio/projects/${saved.id}`)}
    else{saved=await props.adminClient.updateProject(props.id??'',{...input(parsed),version:parsed.version??0});drafts.clear(props.id??null)}
    apply(saved);dirty.value=false
  }catch(error){if(error instanceof AdminApiError&&error.code==='stale_version')stale.value=true
    else if(error instanceof AdminApiError&&error.code==='duplicate_slug')errorMessage.value='Slug is already in use'
    else errorMessage.value='Unable to save project'}finally{saving.value=false}}
async function reload(){const result=await project.refetch();if(result.data){drafts.clear(props.id??null);dirty.value=false;stale.value=false;apply(result.data)}}
async function changeState(action:'publish'|'archive'){if(!props.id||form.version===null||saving.value)return;saving.value=true
  try{const at=form.publishMode==='scheduled'&&scheduleValue.value?new Date(scheduleValue.value).toISOString():null
    apply(action==='publish'?await props.adminClient.publishProject(props.id,form.version,at):await props.adminClient.archiveProject(props.id,form.version));dirty.value=false}finally{saving.value=false}}
onBeforeRouteLeave(()=>!dirty.value||globalThis.confirm('Discard unsaved project changes?'))
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
      <form
        data-testid="save-project"
        class="project-form"
        @submit.prevent="save"
      >
        <div
          v-if="stale"
          class="alert"
        >
          <span>The server version changed. Your input is preserved.</span><button
            type="button"
            @click="reload"
          >
            Reload server version
          </button>
        </div>
        <p
          v-if="errorMessage"
          class="alert"
        >
          {{ errorMessage }}
        </p>
        <div class="grid">
          <label class="wide"><span>Title</span><input
            v-model="form.title"
            name="title"
            maxlength="180"
          ></label>
          <label><span>Slug</span><input
            v-model="form.slug"
            name="slug"
            maxlength="120"
          ></label>
          <label><span>Sort order</span><input
            v-model.number="form.sortOrder"
            name="sortOrder"
            type="number"
            min="0"
          ></label>
          <label class="wide"><span>Summary</span><textarea
            v-model="form.summary"
            name="summary"
            rows="3"
            maxlength="500"
          /></label>
          <label><span>Project URL</span><input
            v-model="form.projectUrl"
            name="projectUrl"
            type="url"
            maxlength="2048"
          ></label>
          <label><span>Repository URL</span><input
            v-model="form.repositoryUrl"
            name="repositoryUrl"
            type="url"
            maxlength="2048"
          ></label>
          <label class="check"><input
            v-model="form.featured"
            type="checkbox"
          > Featured</label>
          <label class="wide"><span>Description</span><MarkdownEditor
            v-model="form.descriptionMarkdown"
            :preview="adminClient.previewProject"
          /></label>
        </div>
        <fieldset v-if="!isNew&&currentStatus==='DRAFT'">
          <legend>Publication</legend>
          <label><input
            v-model="form.publishMode"
            type="radio"
            value="now"
          > Now</label><label><input
            v-model="form.publishMode"
            type="radio"
            value="scheduled"
          > Schedule</label>
          <input
            v-if="form.publishMode==='scheduled'"
            v-model="scheduleValue"
            type="datetime-local"
          ><button
            type="button"
            :disabled="saving"
            @click="changeState('publish')"
          >
            Publish
          </button>
        </fieldset>
        <div class="actions">
          <button
            class="primary"
            type="submit"
            :disabled="saving"
          >
            {{ saving?'Saving':'Save' }}
          </button><button
            v-if="currentStatus==='PUBLISHED'"
            type="button"
            :disabled="saving"
            @click="changeState('archive')"
          >
            Archive
          </button>
        </div>
      </form>
    </AdminPageState>
  </section>
</template>

<style scoped>
.page-head{display:flex;align-items:end;justify-content:space-between;gap:16px;margin-bottom:24px}.page-head p{margin:0;color:#2f7758;font-size:12px;font-weight:750;text-transform:uppercase}.page-head h1{margin:5px 0 0;font-size:30px;overflow-wrap:anywhere}.project-form{min-width:0}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:15px}.wide{grid-column:1/-1}label{display:grid;gap:5px;min-width:0;color:#59645d;font-size:12px;font-weight:700}input,textarea{width:100%;min-width:0;padding:9px;border:1px solid #aeb8b1;border-radius:4px;overflow-wrap:anywhere}.check{display:flex;align-items:center}.check input,fieldset input{width:auto}.alert{display:flex;justify-content:space-between;gap:12px;padding:11px 13px;border-left:3px solid #a33c34;background:#fff4f2;color:#76261f}fieldset{display:flex;flex-wrap:wrap;gap:12px;align-items:end;margin-top:20px;border:1px solid #d8dfda}.actions{display:flex;gap:8px;margin-top:22px;padding-top:18px;border-top:1px solid #d8dfda}button{min-height:38px;padding:8px 12px;border:1px solid #aeb8b1;border-radius:4px;background:#fff}.primary{background:#202622;color:#fff}@media(max-width:680px){.page-head{align-items:flex-start;flex-direction:column}.grid{grid-template-columns:minmax(0,1fr)}.wide{grid-column:auto}.alert{flex-direction:column}}
</style>
