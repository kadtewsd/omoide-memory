/** 写真選択中 */
export class SelectingState {}

/** プレビュー確認中 */
export class PreviewingState {}

/** アルバム作成中 */
export class CreatingState {
    constructor(readonly message: string) {}
}

export type PhotobookState = SelectingState | PreviewingState | CreatingState;
