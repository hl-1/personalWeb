<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminClient } from '../../features/admin/admin-client'
import { parseSkillForm, type SkillForm } from '../../features/admin/portfolio/portfolio-form-schema'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
const props=defineProps<{adminClient:AdminClient}>();const skills=useQuery({queryKey:['admin','skills'],queryFn:()=>props.adminClient.listSkills(),retry:false})
const form=reactive<SkillForm>({name:'',category:'',summary:null,sortOrder:0,visible:true,version:null})
const editing=ref<string>();const saving=ref(false);const errorMessage=ref<string>()
const state=computed<'loading'|'empty'|'error'|'ready'>(()=>skills.isPending.value?'loading':skills.isError.value?'error':skills.data.value?.length?'ready':'empty')
function reset(){Object.assign(form,{name:'',category:'',summary:null,sortOrder:0,visible:true,version:null});editing.value=undefined;errorMessage.value=undefined}
function edit(row:{id:string;name:string;category:string;summary:string|null;sortOrder:number;visible:boolean;version:number}){editing.value=row.id;Object.assign(form,row)}
async function save(){if(saving.value)return;saving.value=true;errorMessage.value=undefined
  try{const parsed=parseSkillForm(form);if(editing.value)await props.adminClient.updateSkill(editing.value,{...parsed,version:parsed.version??0})
    else await props.adminClient.createSkill({...parsed,summary:parsed.summary});reset();await skills.refetch()}
  catch(error){errorMessage.value=error instanceof AdminApiError&&error.code==='stale_version'?'Reload the skill before saving':'Unable to save skill'}finally{saving.value=false}}
async function remove(id:string,version:number){if(!globalThis.confirm('Delete this skill?'))return;await props.adminClient.deleteSkill(id,version);await skills.refetch()}
</script>
<template>
  <section>
    <header class="head">
      <p>Portfolio</p><h1>Skills</h1>
    </header>
    <form
      data-testid="create-skill"
      class="editor"
      @submit.prevent="save"
    >
      <input
        v-model="form.name"
        name="name"
        placeholder="Name"
      ><input
        v-model="form.category"
        name="category"
        placeholder="Category"
      ><input
        v-model="form.summary"
        name="summary"
        placeholder="Summary"
      ><input
        v-model.number="form.sortOrder"
        name="sortOrder"
        type="number"
        min="0"
      ><label><input
        v-model="form.visible"
        type="checkbox"
      > Visible</label><button
        type="submit"
        :disabled="saving"
      >
        {{ editing?'Save':'Add skill' }}
      </button><button
        v-if="editing"
        type="button"
        @click="reset"
      >
        Cancel
      </button>
    </form>
    <p
      v-if="errorMessage"
      class="error"
    >
      {{ errorMessage }}
    </p>
    <AdminPageState
      :state="state"
      empty-label="No skills"
      @retry="skills.refetch()"
    >
      <div class="rows">
        <article
          v-for="skill in skills.data.value"
          :key="skill.id"
        >
          <div><strong>{{ skill.name }}</strong><span>{{ skill.category }}</span></div><span>Order {{ skill.sortOrder }}</span><span>{{ skill.visible?'Visible':'Hidden' }}</span><div>
            <button
              type="button"
              @click="edit(skill)"
            >
              Edit
            </button><button
              type="button"
              @click="remove(skill.id,skill.version)"
            >
              Delete
            </button>
          </div>
        </article>
      </div>
    </AdminPageState>
  </section>
</template>
<style scoped>.head{margin-bottom:20px}.head p{margin:0;color:#2f7758;font-size:12px;font-weight:750;text-transform:uppercase}.head h1{margin:5px 0 0;font-size:30px}.editor{display:grid;grid-template-columns:repeat(3,minmax(120px,1fr)) 90px auto auto;gap:8px;align-items:center;margin-bottom:18px}.editor input,.editor button{min-width:0;min-height:38px;padding:7px 9px;border:1px solid #aeb8b1;border-radius:4px}.editor label{display:flex;gap:5px}.error{color:#842f29}.rows{border-top:1px solid #d8dfda}.rows article{display:grid;grid-template-columns:minmax(0,1fr) 90px 80px auto;gap:12px;align-items:center;padding:13px 4px;border-bottom:1px solid #d8dfda;overflow-wrap:anywhere}.rows article div:first-child span{display:block;color:#68736c;font-size:12px}.rows button{margin-left:5px;padding:6px 9px;border:1px solid #aeb8b1;background:#fff;border-radius:4px}@media(max-width:760px){.editor,.rows article{grid-template-columns:minmax(0,1fr)}}</style>
