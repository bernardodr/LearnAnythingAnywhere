import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { apiPut } from '@/lib/api';
import { useSession } from '@/lib/session';
import type { Textbook } from '@/lib/types';

/**
 * Arranging the library, from wherever a book is moved: dragged in the library, or "Move earlier / later" in a book's
 * ••• menu. The new order shows at once (the `['textbooks']` query the library reads), and the server keeps it
 * (`PUT /textbooks/order`). If the server can't, `failed` turns true and the library goes back to the server's order.
 *
 * @returns `arrange(ids)` to save a whole new order; `move(id, 'earlier' | 'later')` to move one book a place (it
 *   returns false when the book is already at that end or isn't loaded); and `failed`
 */
export function useArrange() {
  const { token } = useSession();
  const queryClient = useQueryClient();
  const [failed, setFailed] = useState(false);

  const arrange = (ids: string[]) => {
    const books = queryClient.getQueryData<Textbook[]>(['textbooks']) ?? [];
    const byId = new Map(books.map((b) => [b.id, b]));
    queryClient.setQueryData<Textbook[]>(['textbooks'], ids.flatMap((id) => byId.get(id) ?? []));
    setFailed(false);
    apiPut('/textbooks/order', { ids }, token).catch(() => {
      setFailed(true);
      void queryClient.invalidateQueries({ queryKey: ['textbooks'] });
    });
  };

  const move = (id: string, to: 'earlier' | 'later') => {
    const ids = (queryClient.getQueryData<Textbook[]>(['textbooks']) ?? []).map((b) => b.id);
    const from = ids.indexOf(id);
    const next = to === 'earlier' ? from - 1 : from + 1;
    if (from < 0 || next < 0 || next >= ids.length) return false;
    [ids[from], ids[next]] = [ids[next], ids[from]];
    arrange(ids);
    return true;
  };

  return { arrange, move, failed };
}
