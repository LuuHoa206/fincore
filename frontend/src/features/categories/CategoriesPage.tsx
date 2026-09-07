import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, ArrowDownLeft, ArrowUpRight, CircleAlert, LoaderCircle, Pencil, Plus, Tag, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { getApiErrorMessage } from '../../shared/api/apiError'
import { categoryApi } from './categoryApi'
import { categoryTypes, type Category, type CategoryType, type CreateCategoryInput } from './categoryTypes'

const categorySchema = z.object({
  name: z.string().trim().min(2, 'Tên danh mục cần ít nhất 2 ký tự').max(80),
  categoryType: z.enum(categoryTypes),
  icon: z.string().trim().regex(/^[a-z0-9-]*$/, 'Biểu tượng chỉ dùng chữ thường, số hoặc dấu gạch ngang').max(50),
  color: z.string().regex(/^#[0-9A-Fa-f]{6}$/, 'Hãy chọn một màu hợp lệ'),
})

type CategoryForm = z.input<typeof categorySchema>
type Filter = 'ALL' | CategoryType

export function CategoriesPage() {
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState<Filter>('ALL')
  const [formOpen, setFormOpen] = useState(false)
  const [editingCategory, setEditingCategory] = useState<Category | null>(null)
  const [archiveTarget, setArchiveTarget] = useState<Category | null>(null)
  const [actionError, setActionError] = useState('')
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: () => categoryApi.list() })
  const archiveMutation = useMutation({
    mutationFn: categoryApi.archive,
    onSuccess: () => {
      setArchiveTarget(null)
      void queryClient.invalidateQueries({ queryKey: ['categories'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Không thể lưu trữ danh mục.')),
  })

  const categories = useMemo(() => (categoriesQuery.data ?? []).filter((category) => filter === 'ALL' || category.categoryType === filter), [categoriesQuery.data, filter])
  const openCreate = () => {
    setEditingCategory(null)
    setFormOpen(true)
  }

  return <>
    <header className="topbar wallet-topbar">
      <div><p className="eyebrow">PHÂN LOẠI DÒNG TIỀN</p><h1>Danh mục</h1><p className="page-subtitle">Phân loại từng khoản thu và chi để hũ tiền, ngân sách và báo cáo sau này luôn dựa trên cùng một nguồn dữ liệu.</p></div>
      <button className="primary-button" onClick={openCreate}><Plus /> Thêm danh mục</button>
    </header>

    {actionError && <div className="form-alert page-alert" role="alert">{actionError}<button onClick={() => setActionError('')} aria-label="Đóng"><X /></button></div>}
    {categoriesQuery.isPending && <div className="content-state"><LoaderCircle className="spin" /><span>Đang tải danh mục</span></div>}
    {categoriesQuery.isError && <div className="content-state error-state"><CircleAlert /><strong>Chưa thể tải danh mục</strong><p>{getApiErrorMessage(categoriesQuery.error, 'Vui lòng thử lại.')}</p><button className="secondary-button" onClick={() => void categoriesQuery.refetch()}>Thử lại</button></div>}
    {!categoriesQuery.isPending && !categoriesQuery.isError && <section className="panel category-workspace">
      <div className="category-filter segmented-control" aria-label="Lọc danh mục">
        {([{ value: 'ALL', label: 'Tất cả' }, { value: 'EXPENSE', label: 'Chi tiêu' }, { value: 'INCOME', label: 'Thu nhập' }] as const).map((item) =>
          <button key={item.value} className={filter === item.value ? 'selected' : ''} onClick={() => setFilter(item.value)}>{item.label}</button>,
        )}
      </div>
      {categories.length === 0 && <div className="content-state compact-state"><Tag /><strong>Chưa có danh mục phù hợp</strong><p>Thêm một danh mục riêng để phân loại giao dịch theo cách bạn muốn.</p><button className="primary-button" onClick={openCreate}><Plus /> Thêm danh mục</button></div>}
      {!!categories.length && <div className="category-grid">{categories.map((category) => <CategoryCard key={category.id} category={category} onEdit={() => { setEditingCategory(category); setFormOpen(true) }} onArchive={() => setArchiveTarget(category)} />)}</div>}
    </section>}

    {formOpen && <CategoryFormModal category={editingCategory} onClose={() => setFormOpen(false)} onSaved={() => { setFormOpen(false); void queryClient.invalidateQueries({ queryKey: ['categories'] }) }} />}
    {archiveTarget && <ConfirmArchiveModal category={archiveTarget} isPending={archiveMutation.isPending} onCancel={() => setArchiveTarget(null)} onConfirm={() => archiveMutation.mutate(archiveTarget.id)} />}
  </>
}

function CategoryCard({ category, onEdit, onArchive }: { category: Category; onEdit: () => void; onArchive: () => void }) {
  const income = category.categoryType === 'INCOME'
  return <article className="category-card">
    <div className="category-card-leading"><span style={{ color: category.color ?? undefined, backgroundColor: `${category.color ?? '#0F8F72'}18` }}>{income ? <ArrowDownLeft /> : <ArrowUpRight />}</span><div><strong>{category.name}</strong><small>{income ? 'Thu nhập' : 'Chi tiêu'}{category.systemCategory ? ' · Mặc định hệ thống' : ' · Danh mục riêng'}</small></div></div>
    {!category.systemCategory && <div className="row-actions"><button className="icon-button" title="Sửa danh mục" aria-label={`Sửa ${category.name}`} onClick={onEdit}><Pencil /></button><button className="icon-button danger-icon" title="Lưu trữ danh mục" aria-label={`Lưu trữ ${category.name}`} onClick={onArchive}><Archive /></button></div>}
  </article>
}

function CategoryFormModal({ category, onClose, onSaved }: { category: Category | null; onClose: () => void; onSaved: () => void }) {
  const [submitError, setSubmitError] = useState('')
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<CategoryForm>({
    resolver: zodResolver(categorySchema),
    defaultValues: defaultsFor(category),
  })

  useEffect(() => reset(defaultsFor(category)), [category, reset])

  const onSubmit = handleSubmit(async (rawValues) => {
    setSubmitError('')
    const values = categorySchema.parse(rawValues)
    try {
      if (category) {
        await categoryApi.update(category.id, { name: values.name, icon: values.icon || undefined, color: values.color })
      } else {
        await categoryApi.create(values as CreateCategoryInput)
      }
      onSaved()
    } catch (error) {
      setSubmitError(getApiErrorMessage(error, category ? 'Không thể cập nhật danh mục.' : 'Không thể tạo danh mục.'))
    }
  })

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="category-form-title">
    <header><div><p className="eyebrow">PHÂN LOẠI GIAO DỊCH</p><h2 id="category-form-title">{category ? 'Chỉnh sửa danh mục' : 'Thêm danh mục'}</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng"><X /></button></header>
    <form className="wallet-form" onSubmit={onSubmit} noValidate>
      {submitError && <div className="form-alert" role="alert">{submitError}</div>}
      <label><span>Tên danh mục</span><input placeholder="Ví dụ: Ăn uống" {...register('name')} />{errors.name && <small className="field-error">{errors.name.message}</small>}</label>
      <div className="form-row"><label><span>Loại</span><select disabled={!!category} {...register('categoryType')}><option value="EXPENSE">Chi tiêu</option><option value="INCOME">Thu nhập</option></select></label><label><span>Màu nhận diện</span><input type="color" className="color-input" {...register('color')} />{errors.color && <small className="field-error">{errors.color.message}</small>}</label></div>
      <label><span>Tên biểu tượng</span><input placeholder="Ví dụ: utensils" {...register('icon')} />{errors.icon && <small className="field-error">{errors.icon.message}</small>}<small>Dùng tên icon dạng chữ thường, ví dụ: utensils, car-front, heart-pulse.</small></label>
      <footer><button type="button" className="plain-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={isSubmitting}>{isSubmitting ? <LoaderCircle className="spin" /> : category ? 'Lưu thay đổi' : 'Tạo danh mục'}</button></footer>
    </form>
  </section></div>
}

function ConfirmArchiveModal({ category, isPending, onCancel, onConfirm }: { category: Category; isPending: boolean; onCancel: () => void; onConfirm: () => void }) {
  return <div className="modal-backdrop" role="presentation"><section className="modal confirm-modal" role="alertdialog" aria-modal="true" aria-labelledby="archive-category-title"><span className="confirm-icon"><Archive /></span><h2 id="archive-category-title">Lưu trữ “{category.name}”?</h2><p>Danh mục sẽ không còn được chọn cho giao dịch mới. Những giao dịch cũ vẫn giữ nguyên thông tin để đối soát.</p><footer><button className="plain-button" onClick={onCancel}>Giữ lại</button><button className="danger-button" onClick={onConfirm} disabled={isPending}>{isPending ? <LoaderCircle className="spin" /> : 'Lưu trữ'}</button></footer></section></div>
}

function defaultsFor(category: Category | null): CategoryForm {
  return {
    name: category?.name ?? '',
    categoryType: category?.categoryType ?? 'EXPENSE',
    icon: category?.icon ?? '',
    color: category?.color ?? '#0F8F72',
  }
}
