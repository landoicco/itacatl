import { useState } from "react";
import type { FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useCreateItem, useUpdateItem } from "@/api/manage";
import { errorCode, fieldErrors } from "@/api/errors";
import type { CatalogItem } from "@/api/types";
import Sheet from "@/components/Sheet";

interface ItemSheetProps {
  slug: string;
  /** The item to edit; a new one when absent */
  item?: CatalogItem;
  open: boolean;
  onClose: () => void;
}

/** Creates or edits a catalog item. Limits mirror ItemRequestDTO, so most errors never leave */
export default function ItemSheet({ slug, item, open, onClose }: ItemSheetProps) {
  const { t } = useTranslation();
  return (
    <Sheet open={open} onClose={onClose} title={t(item ? "catalog.editItem" : "catalog.newItem")}>
      <ItemForm slug={slug} item={item} onSaved={onClose} />
    </Sheet>
  );
}

const inputClass =
  "min-h-12 rounded-control border-2 border-soft bg-surface px-4 text-[15px] font-semibold outline-none focus:border-accent aria-invalid:border-low";

function ItemForm({
  slug,
  item,
  onSaved,
}: {
  slug: string;
  item?: CatalogItem;
  onSaved: () => void;
}) {
  const { t } = useTranslation();
  const create = useCreateItem(slug);
  const update = useUpdateItem(slug);
  const save = item ? update : create;

  // Controlled, so the values survive a rejected save
  const [name, setName] = useState(item?.name ?? "");
  const [description, setDescription] = useState(item?.description ?? "");
  const [price, setPrice] = useState(item ? String(item.suggestedPrice) : "");
  const [alcoholic, setAlcoholic] = useState(item?.alcoholic ?? false);

  const errors = fieldErrors(save.error);
  // Errors that belong to no field: a rule, the network, a denied request
  const formError = save.error && Object.keys(errors).length === 0 ? save.error : null;

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const request = {
      name,
      // The backend stores a blank description as none; sending null says so explicitly
      description: description.trim() ? description : null,
      suggestedPrice: Number(price),
      alcoholic,
    };
    const options = { onSuccess: onSaved };
    if (item) {
      update.mutate({ itemId: item.id, item: request }, options);
    } else {
      create.mutate(request, options);
    }
  };

  const fieldError = (field: string) =>
    errors[field] && (
      <span id={`${field}-error`} className="text-xs font-bold text-low">
        {t(`fieldErrors.${errors[field]}`)}
      </span>
    );

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      {formError && (
        <p role="alert" className="rounded-card bg-soft px-4 py-3 text-sm font-bold text-low">
          {t(`apiErrors.${errorCode(formError)}`)}
        </p>
      )}

      {/* The error sits outside the label, so it is not part of the input's name */}
      <div className="flex flex-col gap-1.5">
        <label className="flex flex-col gap-1.5 text-sm font-bold">
          {t("catalog.form.name")}
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            required
            maxLength={120}
            aria-invalid={Boolean(errors.name)}
            aria-describedby={errors.name && "name-error"}
            className={inputClass}
          />
        </label>
        {fieldError("name")}
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="flex flex-col gap-1.5 text-sm font-bold">
          <span>
            {t("catalog.form.description")}{" "}
            <span className="font-semibold text-muted">{t("catalog.form.optional")}</span>
          </span>
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={2000}
            rows={3}
            aria-invalid={Boolean(errors.description)}
            aria-describedby={errors.description && "description-error"}
            className={`${inputClass} rounded-card py-3`}
          />
        </label>
        {fieldError("description")}
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="flex flex-col gap-1.5 text-sm font-bold">
          {t("catalog.form.suggestedPrice")}
          <input
            type="number"
            inputMode="decimal"
            min={0}
            step={0.01}
            value={price}
            onChange={(event) => setPrice(event.target.value)}
            required
            aria-invalid={Boolean(errors.suggestedPrice)}
            aria-describedby={errors.suggestedPrice && "suggestedPrice-error"}
            className={inputClass}
          />
        </label>
        {fieldError("suggestedPrice")}
      </div>

      <label className="flex items-center gap-3 text-sm font-bold">
        <input
          type="checkbox"
          checked={alcoholic}
          onChange={(event) => setAlcoholic(event.target.checked)}
          className="size-5 accent-accent"
        />
        <span className="flex flex-col">
          {t("menu.alcohol")}
          <span className="font-semibold text-muted">{t("catalog.form.alcoholicHint")}</span>
        </span>
      </label>

      <button
        type="submit"
        disabled={save.isPending}
        className="min-h-12 cursor-pointer rounded-control border-theme bg-accent font-bold text-on-accent shadow-pressed disabled:opacity-60"
      >
        {save.isPending ? t("catalog.form.saving") : t("catalog.form.save")}
      </button>
    </form>
  );
}
