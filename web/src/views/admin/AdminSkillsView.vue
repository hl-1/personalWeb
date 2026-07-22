<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminClient } from '../../features/admin/admin-client'
import { skillFormSchema, type SkillForm } from '../../features/admin/portfolio/portfolio-form-schema'
import { createAdminFormValidation, focusAdminField } from '../../features/admin/admin-form-validation'
import AdminFormField from '../../features/admin/components/AdminFormField.vue'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import { useAdminOperationFeedback } from '../../features/admin/admin-operation-feedback'
import { confirmAdminAction } from '../../features/admin/admin-confirmation'
const props=defineProps<{adminClient:AdminClient}>();const skills=useQuery({queryKey:['admin','skills'],queryFn:()=>props.adminClient.listSkills(),retry:false})
const feedback=useAdminOperationFeedback()
const form=reactive<SkillForm>({name:'',category:'',summary:null,sortOrder:0,visible:true,version:null})
const validation=createAdminFormValidation(skillFormSchema)
const fieldOrder=['name','category','summary','sortOrder']
const editing=ref<string>();const saving=ref(false);const errorMessage=ref<string>()
const state=computed<'loading'|'empty'|'error'|'ready'>(()=>skills.isPending.value?'loading':skills.isError.value?'error':skills.data.value?.length?'ready':'empty')
function validationInput(){return{...form}}
function reset(){Object.assign(form,{name:'',category:'',summary:null,sortOrder:0,visible:true,version:null});editing.value=undefined;errorMessage.value=undefined;validation.resetValidation()}
function edit(row:{id:string;name:string;category:string;summary:string|null;sortOrder:number;visible:boolean;version:number}){editing.value=row.id;Object.assign(form,row);validation.resetValidation()}
function editById(id:string){const row=skills.data.value?.find(item=>item.id===id);if(row)edit(row)}
async function save(){if(saving.value)return;errorMessage.value=undefined
  const result=validation.validateForSubmit(validationInput(),fieldOrder)
  if(!result.success){errorMessage.value='Review the highlighted fields';await focusAdminField(result.firstInvalidField);return}
  saving.value=true
  const operation=editing.value?'skill.update':'skill.create'
  try{const parsed=result.data;if(editing.value)await props.adminClient.updateSkill(editing.value,{...parsed,version:parsed.version??0})
    else await props.adminClient.createSkill({...parsed,summary:parsed.summary});feedback.succeeded(operation);reset();await skills.refetch()}
  catch(error){feedback.failed(operation);if(error instanceof AdminApiError&&error.fieldErrors){errorMessage.value='Review the highlighted fields';await focusAdminField(validation.applyServerErrors(error.fieldErrors))}
    else errorMessage.value=error instanceof AdminApiError&&error.code==='stale_version'?'Reload the skill before saving':'Unable to save skill'}finally{saving.value=false}}
async function remove(id:string,version:number){const result=await confirmAdminAction({title:'删除技能',message:'确定删除这项技能吗？',confirmButtonText:'删除',cancelButtonText:'取消',type:'warning'});if(result!=='confirmed')return;errorMessage.value=undefined
  try{await props.adminClient.deleteSkill(id,version);feedback.succeeded('skill.delete');await skills.refetch()}
  catch{feedback.failed('skill.delete');errorMessage.value='Unable to delete skill'}}
</script>
<template>
  <section>
    <header class="head">
      <p>Portfolio</p><h1>Skills</h1>
    </header>
    <el-form
      data-testid="create-skill"
      class="editor"
      @submit.prevent="save"
    >
      <AdminFormField
        name="name"
        label="Name"
        required
        hint="Required; up to 120 characters"
        :errors="validation.errorsFor('name')"
      >
        <template #default="{controlId,describedBy,invalid}">
          <el-input
            :id="controlId"
            v-model="form.name"
            name="name"
            maxlength="120"
            :aria-describedby="describedBy"
            :aria-invalid="invalid"
            @blur="validation.touch('name',validationInput())"
            @input="validation.change('name',validationInput())"
          />
        </template>
      </AdminFormField><AdminFormField
        name="category"
        label="Category"
        required
        hint="Required; up to 120 characters"
        :errors="validation.errorsFor('category')"
      >
        <template #default="{controlId,describedBy,invalid}">
          <el-input
            :id="controlId"
            v-model="form.category"
            name="category"
            maxlength="120"
            :aria-describedby="describedBy"
            :aria-invalid="invalid"
            @blur="validation.touch('category',validationInput())"
            @input="validation.change('category',validationInput())"
          />
        </template>
      </AdminFormField><AdminFormField
        name="summary"
        label="Summary"
        hint="Optional; up to 500 characters"
        :errors="validation.errorsFor('summary')"
      >
        <template #default="{controlId,describedBy,invalid}">
          <el-input
            :id="controlId"
            v-model="form.summary"
            name="summary"
            maxlength="500"
            :aria-describedby="describedBy"
            :aria-invalid="invalid"
            @blur="validation.touch('summary',validationInput())"
            @input="validation.change('summary',validationInput())"
          />
        </template>
      </AdminFormField><AdminFormField
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
      </AdminFormField><el-checkbox v-model="form.visible">
        Visible
      </el-checkbox><el-button
        type="primary"
        native-type="submit"
        :disabled="saving"
      >
        {{ editing?'Save':'Add skill' }}
      </el-button><el-button
        v-if="editing"
        @click="reset"
      >
        Cancel
      </el-button>
    </el-form>
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
      <el-table
        class="rows"
        :data="skills.data.value ?? []"
        row-key="id"
      >
        <el-table-column
          label="Skill"
          min-width="220"
        >
          <template #default="{ row: skill }">
            <div class="skill-name">
              <strong>{{ skill.name }}</strong><span>{{ skill.category }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="sortOrder"
          label="Order"
          width="100"
        />
        <el-table-column
          label="Visibility"
          width="120"
        >
          <template #default="{ row: skill }">
            <el-tag
              :type="skill.visible ? 'success' : 'info'"
              effect="plain"
            >
              {{ skill.visible?'Visible':'Hidden' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="Actions"
          width="180"
          fixed="right"
        >
          <template #default="{ row: skill }">
            <el-button
              size="small"
              @click="editById(skill.id)"
            >
              Edit
            </el-button><el-button
              size="small"
              type="danger"
              plain
              @click="remove(skill.id,skill.version)"
            >
              Delete
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </AdminPageState>
  </section>
</template>
<style scoped>.head{margin-bottom:20px}.head p{margin:0;color:#2f7758;font-size:12px;font-weight:750;text-transform:uppercase}.head h1{margin:5px 0 0;font-size:30px}.editor{display:grid;grid-template-columns:repeat(3,minmax(120px,1fr)) 120px auto auto;gap:8px;align-items:center;margin-bottom:18px}.error{color:#842f29}.skill-name{display:grid;gap:3px}.skill-name span{color:#68736c;font-size:12px}@media(max-width:760px){.editor{grid-template-columns:minmax(0,1fr)}}</style>
