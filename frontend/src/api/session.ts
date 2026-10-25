import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./client";
import { queryKeys } from "./queries";

/*
 * The session is server state like any other: the backend owns it (a cookie and a row in
 * PostgreSQL), and the app only caches who is signed in. So it is a query, not a React context.
 */

/** The signed-in user; `data` is null without a session */
export function useCurrentUser() {
  return useQuery({
    queryKey: queryKeys.currentUser,
    queryFn: api.getCurrentUser,
    // Memberships change rarely; a stale copy only affects which businesses are listed, and
    // the backend checks every call anyway
    staleTime: 5 * 60_000,
  });
}

export function useSignIn() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: api.signIn,
    // The response is the user, so there is nothing to refetch
    onSuccess: (user) => queryClient.setQueryData(queryKeys.currentUser, user),
  });
}

export function useSignOut() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: api.signOut,
    onSuccess: () => {
      queryClient.setQueryData(queryKeys.currentUser, null);
      // Nothing of the previous user's businesses may outlive the session on a shared phone
      queryClient.removeQueries({ queryKey: queryKeys.manage });
    },
  });
}
