export const DEV_USER = {
  id: "dev-user-001",
  name: "Dev User",
  email: "dev@localhost",
} as const;

export type AppUser = {
  id: string;
  name: string;
  email: string;
};
