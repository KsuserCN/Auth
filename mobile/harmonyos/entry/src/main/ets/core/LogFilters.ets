import { UserFacingError } from './Models';

export interface LogFilter { operation: string; result: string; startDate: string; endDate: string; }
export function emptyLogFilter(): LogFilter { return { operation: '', result: '', startDate: '', endDate: '' }; }
function copy(value: LogFilter): LogFilter {
  return { operation: value.operation, result: value.result, startDate: value.startDate, endDate: value.endDate };
}
function validDate(value: string): boolean {
  if (!value) { return true; }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) { return false; }
  const date = new Date(value + 'T12:00:00');
  return Number.isFinite(date.getTime()) && date.getFullYear() === Number(value.slice(0, 4)) &&
    date.getMonth() + 1 === Number(value.slice(5, 7)) && date.getDate() === Number(value.slice(8, 10));
}
@Observed
export class LogFilters {
  applied: LogFilter = emptyLogFilter();
  draft: LogFilter = emptyLogFilter();
  begin(): void { this.draft = copy(this.applied); }
  cancel(): void { this.draft = copy(this.applied); }
  resetDraft(): void { this.draft = emptyLogFilter(); }
  clear(): void { this.applied = emptyLogFilter(); this.draft = emptyLogFilter(); }
  update(field: string, value: string): void {
    const next = copy(this.draft);
    switch (field) {
      case 'operation': next.operation = value; break;
      case 'result': next.result = value; break;
      case 'startDate': next.startDate = value; break;
      case 'endDate': next.endDate = value; break;
    }
    this.draft = next;
  }
  apply(): void {
    if (!validDate(this.draft.startDate) || !validDate(this.draft.endDate)) { throw new UserFacingError('请选择有效日期'); }
    if (this.draft.startDate && this.draft.endDate && this.draft.startDate > this.draft.endDate) {
      throw new UserFacingError('开始日期不能晚于结束日期');
    }
    this.applied = copy(this.draft);
  }
}
