<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="家庭ID" prop="familyId">
        <el-input
          v-model="queryParams.familyId"
          placeholder="请输入家庭ID"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="用户ID" prop="usrId">
        <el-input
          v-model="queryParams.usrId"
          placeholder="请输入用户ID"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="角色代码" prop="roleCode">
        <el-input
          v-model="queryParams.roleCode"
          placeholder="请输入角色代码"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="用户名称" prop="usrName">
        <el-input
          v-model="queryParams.usrName"
          placeholder="请输入用户名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="成员名称" prop="roleName">
        <el-input
          v-model="queryParams.roleName"
          placeholder="请输入成员名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['family:familyuser:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['family:familyuser:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['family:familyuser:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['family:familyuser:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="familyuserList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="租户号" align="center" prop="familyUserId" />
      <el-table-column label="乐观锁" align="center" prop="version" />
      <el-table-column label="创建人" align="center" prop="createBy" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="更新人" align="center" prop="updateBy" />
      <el-table-column label="更新时间" align="center" prop="updateTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.updateTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="家庭ID" align="center" prop="familyId" />
      <el-table-column label="用户ID" align="center" prop="usrId" />
      <el-table-column label="角色代码" align="center" prop="roleCode" />
      <el-table-column label="用户名称" align="center" prop="usrName" />
      <el-table-column label="成员名称" align="center" prop="roleName" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['family:familyuser:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['family:familyuser:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <pagination
      v-show="total>0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改用户家庭关联对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="familyuserRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="家庭ID" prop="familyId">
          <el-input v-model="form.familyId" placeholder="请输入家庭ID" />
        </el-form-item>
        <el-form-item label="用户ID" prop="usrId">
          <el-input v-model="form.usrId" placeholder="请输入用户ID" />
        </el-form-item>
        <el-form-item label="角色代码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="请输入角色代码" />
        </el-form-item>
        <el-form-item label="用户名称" prop="usrName">
          <el-input v-model="form.usrName" placeholder="请输入用户名称" />
        </el-form-item>
        <el-form-item label="成员名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入成员名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AdminFamilyuser">
import { listFamilyuser, getFamilyuser, delFamilyuser, addFamilyuser, updateFamilyuser } from "@/api/family/familyadmin";

const { proxy } = getCurrentInstance();

const familyuserList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref("");

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    familyId: null,
    usrId: null,
    roleCode: null,
    usrName: null,
    roleName: null
  },
  rules: {
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询用户家庭关联列表 */
function getList() {
  loading.value = true;
  listFamilyuser(queryParams.value).then(response => {
    familyuserList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    familyUserId: null,
    version: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null,
    familyId: null,
    usrId: null,
    roleCode: null,
    usrName: null,
    roleName: null
  };
  proxy.resetForm("familyuserRef");
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef");
  handleQuery();
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.familyUserId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加用户家庭关联";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const _familyUserId = row.familyUserId || ids.value
  getFamilyuser(_familyUserId).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改用户家庭关联";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["familyuserRef"].validate(valid => {
    if (valid) {
      if (form.value.familyUserId != null) {
        updateFamilyuser(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addFamilyuser(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功");
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _familyUserIds = row.familyUserId || ids.value;
  proxy.$modal.confirm('是否确认删除用户家庭关联编号为"' + _familyUserIds + '"的数据项？').then(function() {
    return delFamilyuser(_familyUserIds);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('family/familyuser/export', {
    ...queryParams.value
  }, `familyuser_${new Date().getTime()}.xlsx`)
}

getList();
</script>
