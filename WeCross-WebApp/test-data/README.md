# Traffic verification test data

- `traffic-data-1000.csv` contains one header and 1,000 deterministic traffic records.
- In the WebApp file mode, each non-empty CSV line becomes one Merkle leaf, including the header.
- Text types (`txt`, `csv`, `json`, `xml`, `log`, `md`) are split by non-empty lines.
- Other file types are split into 64 KiB byte chunks and each chunk is Base64 encoded before it is sent to the verification API.
- The current browser-side upload limit is 10 MiB.
