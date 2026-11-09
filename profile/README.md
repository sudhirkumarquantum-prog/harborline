# Sudhir Kumar

Principal II Software Architect. I design passenger-service systems where the order of a write, the lock around it, and the cost of the read are the product.

Most of that work is inside Sabre and stays there. The public laboratory is [Harborline](https://github.com/sudhirkumarquantum-prog/harborline): a fictional flight, HB418, used to show the mechanisms with tests.

- Fact dependencies ordered with Kahn's algorithm, cycles refused before anything is written
- Seat locks, then passenger locks, in sorted order — disjoint check-ins overlap, one seat does not
- A host memo that collapses a cabin of identical reads into one in-flight call
- A check-in graph whose current node is the only thing allowed to call a tool
- A cassette that fails when the journey drifts, and a catalog that ranks the decisions in this repo

Google Professional Cloud Architect. Agentic systems, Java, and the unglamorous part of airline modernization: getting a passenger update off a host without losing the consistency the host used to guarantee.

Bengaluru. [sudhirkumar.quantum@gmail.com](mailto:sudhirkumar.quantum@gmail.com)
