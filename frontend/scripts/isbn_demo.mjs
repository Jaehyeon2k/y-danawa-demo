import { buildIsbnSearchPayload } from "../src/utils/isbnSearch.js";

const isbn = process.argv[2] || "9780306406157";

try {
  const result = buildIsbnSearchPayload(isbn);
  console.log(JSON.stringify(result, null, 2));
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
