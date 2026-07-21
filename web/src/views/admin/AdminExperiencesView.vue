<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminClient } from '../../features/admin/admin-client'
import { parseExperienceForm, type ExperienceForm } from '../../features/admin/portfolio/portfolio-form-schema'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import MarkdownEditor from '../../features/admin/components/MarkdownEditor.vue'
const props=defineProps<{adminClient:AdminClient}>();const experiences=useQuery({queryKey:['admin','experiences'],queryFn:()=>props.adminClient.listExperiences(),retry:false})
const form=reactive<ExperienceForm>({organization:'',role:'',startDate:'',endDate:null,summaryMarkdown:'',sortOrder:0,visible:true,version:null})
const editing=ref<string>();const saving=ref(false);const errorMessage=ref<string>()
const state=computed<'loading'|'empty'|'error'|'ready'>(()=>experiences.isPending.value?'loading':experiences.isError.value?'error':experiences.data.value?.length?'ready':'empty')
function reset(){Object.assign(form,{organization:'',role:'',startDate:'',endDate:null,summaryMarkdown:'',sortOrder:0,visible:true,version:null});editing.value=undefined;errorMessage.value=undefined}
function edit(row:{id:string;organization:string;role:string;startDate:string;endDate:string|null;summaryMarkdown:string;sortOrder:number;visible:boolean;version:number}){editing.value=row.id;Object.assign(form,row)}
async function save(){if(saving.value)return;saving.value=true;errorMessage.value=undefined
  try{const parsed=parseExperienceForm(form);if(editing.value)await props.adminClient.updateExperience(editing.value,{...parsed,version:parsed.version??0})
    else await props.adminClient.createExperience(parsed);reset();await experiences.refetch()}
  catch(error){errorMessage.value=error instanceof AdminApiError&&error.code==='stale_version'?'Reload the experience before saving':'Review the experience fields'}finally{saving.value=false}}
async function remove(id:string,version:number){if(!globalThis.confirm('Delete this experience?'))return;await props.adminClient.deleteExperience(id,version);await experiences.refetch()}
</script>
<template>
  <section>
    <header class="head">
      <p>Portfolio</p><h1>Experience</h1>
    </header>
    <form
      class="editor"
      @submit.prevent="save"
    >
      <div class="grid">
        <label>Organization<input
          v-model="form.organization"
          name="organization"
        ></label><label>Role<input
          v-model="form.role"
          name="role"
        ></label><label>Start date<input
          v-model="form.startDate"
          name="startDate"
          type="date"
        ></label><label>End date<input
          v-model="form.endDate"
          name="endDate"
          type="date"
        ></label><label>Sort order<input
          v-model.number="form.sortOrder"
          name="sortOrder"
          type="number"
          min="0"
        ></label><label class="check"><input
          v-model="form.visible"
          type="checkbox"
        > Visible</label><label class="wide">Summary<MarkdownEditor
          v-model="form.summaryMarkdown"
          :preview="adminClient.previewArticle"
        /></label>
      </div><div class="actions">
        <button
          type="submit"
          :disabled="saving"
        >
          {{ editing?'Save':'Add experience' }}
        </button><button
          v-if="editing"
          type="button"
          @click="reset"
        >
          Cancel
        </button>
      </div>
    </form>
    <p
      v-if="errorMessage"
      class="error"
    >
      {{ errorMessage }}
    </p>
    <AdminPageState
      :state="state"
      empty-label="No experience"
      @retry="experiences.refetch()"
    >
      <div
        data-testid="experience-list"
        class="experience-list"
      >
        <article
          v-for="item in experiences.data.value"
          :key="item.id"
        >
          <div><strong>{{ item.organization }}</strong><span>{{ item.role }}</span></div><span>{{ item.startDate }} – {{ item.endDate??'Present' }}</span><span>Order {{ item.sortOrder }}</span><span>{{ item.visible?'Visible':'Hidden' }}</span><div>
            <button
              type="button"
              @click="edit(item)"
            >
              Edit
            </button><button
              type="button"
              @click="remove(item.id,item.version)"
            >
              Delete
            </button>
          </div>
        </article>
      </div>
    </AdminPageState>
  </section>
</template>
<style scoped>.head{margin-bottom:20px}.head p{margin:0;color:#2f7758;font-size:12px;font-weight:750;text-transform:uppercase}.head h1{margin:5px 0 0;font-size:30px}.editor{padding-bottom:18px;border-bottom:1px solid #d8dfda}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.wide{grid-column:1/-1}.grid label{display:grid;gap:5px;min-width:0;color:#59645d;font-size:12px;font-weight:700}.grid input{min-width:0;padding:8px;border:1px solid #aeb8b1;border-radius:4px}.check{display:flex!important;align-items:center}.check input{width:auto}.actions{display:flex;gap:8px;margin-top:12px}.actions button,.experience-list button{padding:7px 10px;border:1px solid #aeb8b1;background:#fff;border-radius:4px}.error{color:#842f29}.experience-list{border-top:1px solid #d8dfda}.experience-list article{display:grid;grid-template-columns:minmax(150px,1fr) minmax(140px,1fr) 80px 70px auto;gap:12px;align-items:center;padding:13px 4px;border-bottom:1px solid #d8dfda;min-width:0;overflow-wrap:anywhere}.experience-list article div:first-child{min-width:0}.experience-list article div:first-child span{display:block;color:#68736c;font-size:12px}.experience-list button{margin-left:5px}@media(max-width:760px){.grid,.experience-list article{grid-template-columns:minmax(0,1fr)}.wide{grid-column:auto}}</style>
