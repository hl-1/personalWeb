<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminClient } from '../../features/admin/admin-client'
import { experienceFormSchema, type ExperienceForm } from '../../features/admin/portfolio/portfolio-form-schema'
import { createAdminFormValidation, focusAdminField } from '../../features/admin/admin-form-validation'
import AdminFormField from '../../features/admin/components/AdminFormField.vue'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import MarkdownEditor from '../../features/admin/components/MarkdownEditor.vue'
import { useAdminOperationFeedback } from '../../features/admin/admin-operation-feedback'
import { confirmAdminAction } from '../../features/admin/admin-confirmation'
const props=defineProps<{adminClient:AdminClient}>();const experiences=useQuery({queryKey:['admin','experiences'],queryFn:()=>props.adminClient.listExperiences(),retry:false})
const feedback=useAdminOperationFeedback()
const form=reactive<ExperienceForm>({organization:'',role:'',startDate:'',endDate:null,summaryMarkdown:'',sortOrder:0,visible:true,version:null})
const validation=createAdminFormValidation(experienceFormSchema)
const fieldOrder=['organization','role','startDate','endDate','sortOrder','summaryMarkdown']
const editing=ref<string>();const saving=ref(false);const errorMessage=ref<string>()
const state=computed<'loading'|'empty'|'error'|'ready'>(()=>experiences.isPending.value?'loading':experiences.isError.value?'error':experiences.data.value?.length?'ready':'empty')
function validationInput(){return{...form}}
function reset(){Object.assign(form,{organization:'',role:'',startDate:'',endDate:null,summaryMarkdown:'',sortOrder:0,visible:true,version:null});editing.value=undefined;errorMessage.value=undefined;validation.resetValidation()}
function edit(row:{id:string;organization:string;role:string;startDate:string;endDate:string|null;summaryMarkdown:string;sortOrder:number;visible:boolean;version:number}){editing.value=row.id;Object.assign(form,row);validation.resetValidation()}
function editById(id:string){const row=experiences.data.value?.find(item=>item.id===id);if(row)edit(row)}
async function save(){if(saving.value)return;errorMessage.value=undefined
  const result=validation.validateForSubmit(validationInput(),fieldOrder)
  if(!result.success){errorMessage.value='Review the highlighted fields';await focusAdminField(result.firstInvalidField);return}
  saving.value=true
  const operation=editing.value?'experience.update':'experience.create'
  try{const parsed=result.data;if(editing.value)await props.adminClient.updateExperience(editing.value,{...parsed,version:parsed.version??0})
    else await props.adminClient.createExperience(parsed);feedback.succeeded(operation);reset();await experiences.refetch()}
  catch(error){feedback.failed(operation);if(error instanceof AdminApiError&&error.fieldErrors){errorMessage.value='Review the highlighted fields';await focusAdminField(validation.applyServerErrors(error.fieldErrors,{dateRangeValid:'endDate'}))}
    else errorMessage.value=error instanceof AdminApiError&&error.code==='stale_version'?'Reload the experience before saving':'Unable to save experience'}finally{saving.value=false}}
async function remove(id:string,version:number){const result=await confirmAdminAction({title:'删除经历',message:'确定删除这段经历吗？',confirmButtonText:'删除',cancelButtonText:'取消',type:'warning'});if(result!=='confirmed')return;errorMessage.value=undefined
  try{await props.adminClient.deleteExperience(id,version);feedback.succeeded('experience.delete');await experiences.refetch()}
  catch{feedback.failed('experience.delete');errorMessage.value='Unable to delete experience'}}
</script>
<template>
  <section>
    <header class="head">
      <p>Portfolio</p><h1>Experience</h1>
    </header>
    <el-form
      class="editor"
      @submit.prevent="save"
    >
      <div class="grid">
        <AdminFormField
          name="organization"
          label="Organization"
          required
          hint="Required; up to 180 characters"
          :errors="validation.errorsFor('organization')"
        >
          <template #default="{ controlId, describedBy, invalid }">
            <el-input
              :id="controlId"
              v-model="form.organization"
              name="organization"
              maxlength="180"
              :aria-describedby="describedBy"
              :aria-invalid="invalid"
              @blur="validation.touch('organization', validationInput())"
              @input="validation.change('organization', validationInput())"
            />
          </template>
        </AdminFormField><AdminFormField
          name="role"
          label="Role"
          required
          hint="Required; up to 180 characters"
          :errors="validation.errorsFor('role')"
        >
          <template #default="{ controlId, describedBy, invalid }">
            <el-input
              :id="controlId"
              v-model="form.role"
              name="role"
              maxlength="180"
              :aria-describedby="describedBy"
              :aria-invalid="invalid"
              @blur="validation.touch('role', validationInput())"
              @input="validation.change('role', validationInput())"
            />
          </template>
        </AdminFormField><AdminFormField
          name="startDate"
          label="Start date"
          required
          hint="Required date"
          :errors="validation.errorsFor('startDate')"
        >
          <template #default="{ controlId, describedBy, invalid }">
            <el-date-picker
              :id="controlId"
              v-model="form.startDate"
              name="startDate"
              type="date"
              value-format="YYYY-MM-DD"
              format="YYYY-MM-DD"
              :aria-describedby="describedBy"
              :aria-invalid="invalid"
              @blur="validation.touch('startDate', validationInput())"
              @input="validation.change('startDate', validationInput())"
            />
          </template>
        </AdminFormField><AdminFormField
          name="endDate"
          label="End date"
          hint="Optional; cannot be before the start date"
          :errors="validation.errorsFor('endDate')"
        >
          <template #default="{ controlId, describedBy, invalid }">
            <el-date-picker
              :id="controlId"
              v-model="form.endDate"
              name="endDate"
              type="date"
              value-format="YYYY-MM-DD"
              format="YYYY-MM-DD"
              clearable
              :aria-describedby="describedBy"
              :aria-invalid="invalid"
              @blur="validation.touch('endDate', validationInput())"
              @input="validation.change('endDate', validationInput())"
            />
          </template>
        </AdminFormField><AdminFormField
          name="sortOrder"
          label="Sort order"
          required
          hint="Zero or a positive whole number"
          :errors="validation.errorsFor('sortOrder')"
        >
          <template #default="{ controlId, describedBy, invalid }">
            <el-input-number
              :id="controlId"
              v-model.number="form.sortOrder"
              name="sortOrder"
              :min="0"
              :step="1"
              :precision="0"
              :aria-describedby="describedBy"
              :aria-invalid="invalid"
              @blur="validation.touch('sortOrder', validationInput())"
              @input="validation.change('sortOrder', validationInput())"
            />
          </template>
        </AdminFormField><el-checkbox v-model="form.visible">
          Visible
        </el-checkbox><AdminFormField
          class="wide"
          name="summaryMarkdown"
          label="Summary"
          hint="Optional; up to 20,000 characters"
          :errors="validation.errorsFor('summaryMarkdown')"
        >
          <template #default="{ controlId, describedBy, invalid }">
            <MarkdownEditor
              :model-value="form.summaryMarkdown"
              name="summaryMarkdown"
              :control-id="controlId"
              :described-by="describedBy"
              :invalid="invalid"
              :preview="adminClient.previewArticle"
              @update:model-value="form.summaryMarkdown=$event;validation.change('summaryMarkdown',validationInput())"
              @blur="validation.touch('summaryMarkdown',validationInput())"
            />
          </template>
        </AdminFormField>
      </div><div class="actions">
        <el-button
          type="primary"
          native-type="submit"
          :disabled="saving"
        >
          {{ editing?'Save':'Add experience' }}
        </el-button><el-button
          v-if="editing"
          @click="reset"
        >
          Cancel
        </el-button>
      </div>
    </el-form>
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
      <el-table
        data-testid="experience-list"
        class="experience-list"
        :data="experiences.data.value ?? []"
        row-key="id"
      >
        <el-table-column
          label="Experience"
          min-width="240"
        >
          <template #default="{ row: item }">
            <div class="experience-name">
              <strong>{{ item.organization }}</strong><span>{{ item.role }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="Dates"
          min-width="190"
        >
          <template #default="{ row: item }">
            {{ item.startDate }} - {{ item.endDate??'Present' }}
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
          <template #default="{ row: item }">
            <el-tag
              :type="item.visible ? 'success' : 'info'"
              effect="plain"
            >
              {{ item.visible?'Visible':'Hidden' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="Actions"
          width="180"
          fixed="right"
        >
          <template #default="{ row: item }">
            <el-button
              size="small"
              @click="editById(item.id)"
            >
              Edit
            </el-button><el-button
              size="small"
              type="danger"
              plain
              @click="remove(item.id,item.version)"
            >
              Delete
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </AdminPageState>
  </section>
</template>
<style scoped>.head{margin-bottom:20px}.head p{margin:0;color:#2f7758;font-size:12px;font-weight:750;text-transform:uppercase}.head h1{margin:5px 0 0;font-size:30px}.editor{padding-bottom:18px;border-bottom:1px solid #d8dfda}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.wide{grid-column:1/-1}.actions{display:flex;gap:8px;margin-top:12px}.error{color:#842f29}.experience-name{display:grid;min-width:0;gap:3px;overflow-wrap:anywhere}.experience-name span{color:#68736c;font-size:12px}@media(max-width:760px){.grid{grid-template-columns:minmax(0,1fr)}.wide{grid-column:auto}}</style>
