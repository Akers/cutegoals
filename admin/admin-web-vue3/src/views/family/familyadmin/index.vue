<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="家庭名称" prop="familyName">
        <el-input
          v-model="queryParams.familyName"
          placeholder="请输入家庭名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="家庭地址" prop="familyAddress">
        <el-input
          v-model="queryParams.familyAddress"
          placeholder="请输入家庭地址"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="small" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="small" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="small"
          @click="handleAdd"
          v-hasPermi="['family:family:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="small"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['family:family:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="small"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['family:family:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="small"
          @click="handleExport"
          v-hasPermi="['family:family:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="familyList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" align="center" prop="id" />
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
      <el-table-column label="家庭名称" align="center" prop="familyName" />
      <el-table-column label="家庭地址" align="center" prop="familyAddress" />
      <el-table-column label="关联用户id" align="center" prop="usrId" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
            size="small"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['family:family:edit']"
          >修改</el-button>
          <el-button
            size="small"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['family:family:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改家庭信息对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="家庭名称" prop="familyName">
          <el-input v-model="form.familyName" placeholder="请输入家庭名称" />
        </el-form-item>
        <el-form-item label="家庭地址" prop="familyAddress">
          <el-input v-model="form.familyAddress" placeholder="请输入家庭地址" />
        </el-form-item>
        <el-divider content-position="center">家庭用户关联表信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="el-icon-plus" size="small" @click="handleAddXhhFamilyUser">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="el-icon-delete" size="small" @click="handleDeleteXhhFamilyUser">删除</el-button>
          </el-col>
        </el-row>
        <el-table :data="xhhFamilyUserList" :row-class-name="rowXhhFamilyUserIndex" @selection-change="handleXhhFamilyUserSelectionChange" ref="xhhFamilyUser">
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column label="序号" align="center" prop="index" width="50"/>
          <el-table-column label="创建人" prop="createBy" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.createBy" placeholder="请输入创建人" />
            </template>
          </el-table-column>
          <el-table-column label="创建时间" prop="createTime" width="240">
            <template #default="scope">
              <el-date-picker clearable v-model="scope.row.createTime" type="date" value-format="yyyy-MM-dd" placeholder="请选择创建时间" />
            </template>
          </el-table-column>
          <el-table-column label="更新人" prop="updateBy" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.updateBy" placeholder="请输入更新人" />
            </template>
          </el-table-column>
          <el-table-column label="更新时间" prop="updateTime" width="240">
            <template #default="scope">
              <el-date-picker clearable v-model="scope.row.updateTime" type="date" value-format="yyyy-MM-dd" placeholder="请选择更新时间" />
            </template>
          </el-table-column>
          <el-table-column label="角色代码" prop="roleCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.roleCode" placeholder="请输入角色代码" />
            </template>
          </el-table-column>
          <el-table-column label="用户名称" prop="usrName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.usrName" placeholder="请输入用户名称" />
            </template>
          </el-table-column>
          <el-table-column label="成员名称" prop="roleName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.roleName" placeholder="请输入成员名称" />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listFamily, getFamily, delFamily, addFamily, updateFamily } from "@/api/family/familyadmin";

export default {
  name: "Family",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 子表选中数据
      checkedXhhFamilyUser: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 家庭信息表格数据
      familyList: [],
      // 家庭用户关联表表格数据
      xhhFamilyUserList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        familyName: null,
        familyAddress: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询家庭信息列表 */
    getList() {
      this.loading = true;
      listFamily(this.queryParams).then(response => {
        this.familyList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        id: null,
        version: null,
        createBy: null,
        createTime: null,
        updateBy: null,
        updateTime: null,
        familyName: null,
        familyAddress: null,
        usrId: null
      };
      this.xhhFamilyUserList = [];
      this.resetForm("form");
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加家庭信息";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids
      getFamily(id).then(response => {
        this.form = response.data;
        this.xhhFamilyUserList = response.data.xhhFamilyUserList;
        this.open = true;
        this.title = "修改家庭信息";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          this.form.xhhFamilyUserList = this.xhhFamilyUserList;
          if (this.form.id != null) {
            updateFamily(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addFamily(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal.confirm('是否确认删除家庭信息编号为"' + ids + '"的数据项？').then(function() {
        return delFamily(ids);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
	/** 家庭用户关联表序号 */
    rowXhhFamilyUserIndex({ row, rowIndex }) {
      row.index = rowIndex + 1;
    },
    /** 家庭用户关联表添加按钮操作 */
    handleAddXhhFamilyUser() {
      let obj = {};
      obj.createBy = "";
      obj.createTime = "";
      obj.updateBy = "";
      obj.updateTime = "";
      obj.roleCode = "";
      obj.usrName = "";
      obj.roleName = "";
      this.xhhFamilyUserList.push(obj);
    },
    /** 家庭用户关联表删除按钮操作 */
    handleDeleteXhhFamilyUser() {
      if (this.checkedXhhFamilyUser.length == 0) {
        this.$modal.msgError("请先选择要删除的家庭用户关联表数据");
      } else {
        const xhhFamilyUserList = this.xhhFamilyUserList;
        const checkedXhhFamilyUser = this.checkedXhhFamilyUser;
        this.xhhFamilyUserList = xhhFamilyUserList.filter(function(item) {
          return checkedXhhFamilyUser.indexOf(item.index) == -1
        });
      }
    },
    /** 复选框选中数据 */
    handleXhhFamilyUserSelectionChange(selection) {
      this.checkedXhhFamilyUser = selection.map(item => item.index)
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('family/family/export', {
        ...this.queryParams
      }, `family_${new Date().getTime()}.xlsx`)
    }
  }
};
</script>
