# ginfer

[![Clojars Project](https://img.shields.io/clojars/v/com.github.s-doti/ginfer.svg)](https://clojars.org/com.github.s-doti/ginfer)

Graph inference library.

## The WHAT and the WHY

This library was conceived for work purposes.
There are of course any number of existing, mature products out there, which tackle this 
very general domain. But after reviewing several options, on the question of 'make it or 
buy it', I was reluctant to pick some off-the-shelf solution, without being 80% sure this 
would cover 80% of the need. Instead of making a choice that would impose integrations 
costing men years, no doubt, I opted to spend a short while experimenting with truly tough 
constraints in mind. My solution would have to be:
- Data storage agnostic
- Zero memory, stateless
- Event-driven
- Execution-style agnostic
- Eventually-consistent
- Descriptive
- Performing at the same orders of magnitude as other solutions known to me

So I spent every spare moment I could, checking off these goals from my list.  
Except for that last one, which isn't feasible to prove early on, I put together a 
whitepaper and started looking for feedback. A miserable attempt, no one understood what 
I was babbling about. A RnD design session concluded with mild sympathetic response. A 
2-day hands-on workshop with stakeholders was fun, but not enough to drive this forward.  
Until I finally managed to secure minimal budget for a 10 weeks 'real-life' demo project. 
It was a one-man show, namely myself, and I was thoroughly surprised by the outcome.  
Not only was it easy to build, it took so little effort that I had plenty of time to push for 
features I'd only dreamed of having; and the level of control I had as a developer over 
the logic execution, was unprecedented in my experience. In such short time, not only did I 
have a demo that was covering 80% of the real-life product, I also had the following baked in:
- Data explain-ability
- Dynamic import of new product logic, from pseudo-english spec
- Pause/resume semantics for cascading graph inference
- DR support
- Live debug at the business logic terms granularity
- And a long list of extra product features not otherwise feasible with the then-existing approach

The fruits of this effort would continue to become a strategic project at the company I had worked 
for, and I've been given permission to open-source my ideas with this generic implementation 
to the world.

I'd characterize the state of this library as 'advanced demo', yet to run in a real 
production env at the time of writing this.

## The HOW

This library builds on 3 smaller libraries (~200 lines of code each) broken out of its initial 
code base:
- [seamless-async](https://github.com/s-doti/seamless-async)
enables code that is written once, but may be executed in either blocking/async fashion
- [persistroids](https://github.com/s-doti/persistroids)
makes any persistence seem like its on steroids; also, abstracts away persistence, makes it 
easy to plug-n-play integrations.
- [sepl](https://github.com/s-doti/sepl)
logic execution engine; in effect, converting logic execution into pure data, which allows for 
advanced features to come into play.

The remaining code (1k-2K lines, which I still call 'light-weight') revolves around the following 
concepts:

#### Blueprints
Your graph structure, its *blueprints*, are expressed as various entity types along with their 
*attributes*. A pair of *[entity id, attribute]* serve as coordinate of an atomic unit in
the graph, which may be notified/evaluated, updated, and notifying changes in its turn.
Beyond their declaration, attributes are smart units; they may be *generic, inferred, or 
referencing* other attributes. For simplicity, any edge in the graph act as a 2-way street, 
explicitly denoted by a pair of symmetric reference attributes. Beyond this, attributes behavior 
is extremely flexible and may be extended as needed. Attributes may also denote endpoints for 
pulling/pushing data, in which case your graph is a distributed global beast.

#### update, notify, and eval
This library runs a naive update-notify-eval loop, applied incrementally onto the graph data, 
until there are no further updates to data. Those 3 steps - *update, notify, eval* - may be 
applied to any location in the graph to kick this basic loop along with any cascading impact. 
Cascading actions may be performed eagerly, or lazily; they may be paused and later resumed, 
but they have to be allowed to unfold in full, to maintain eventual consistency. Concurrency 
of multiple such processes is not yet supported, and I do hope to get to it some day.

#### Connectors
This library is intended to integrate with your storage solution and data model, without 
imposing its own semantics down to your persistence layer. User data may reside in any 
storage solution, in any format. The user need only provide their *connectors*, stateful 
objects which satisfy the general 
[contract](https://github.com/s-doti/persistroids/blob/main/src/persistroids/connector.clj), 
and act as translators between this library and user data. Elaborate demo sessions utilizing 
this code were successfully using documents, relational, and even files based storage 
solutions.<br>
Another form of connectors, are *endpoints connectors*. These serve a similar function, 
but adhere to their own [specification](src/ginfer/connectors/endpoints/connector.clj), and 
are only one-way, either pulling or pushing.

To put this all together, this library takes your *blueprints*, initial *step(s)*, and 
connectors; given those, it successfully applies incremental inference onto your graph data.

** For a much simpler version of the above, [baby-ginfer](https://github.com/s-doti/baby-ginfer) 
largely diminishes the *connectors* and *annotations* concepts, and focuses on 
delivering the update-notify-eval concept as a gist in under 100 lines of code.

## Usage

Lets say for example we wanted to model a basic org chart. We'd start with some structural 
statements, our blueprints for such a model:
```clojure
(def blueprints ["company has-many departments and department has company"
                 "company head-count sum from [[departments head-count]]"
                 "department has-many teams and team has department"
                 "department head-count sum from [[teams head-count]]"
                 "team has-many employees and employee has team"
                 "team head-count count from [[employees]]"
                 "employee allegiance identity from [[team department id]]"])
```
The pseudo english dialect, experimental at current phase, is equivalent to a 
more mature (though less readable) programmatic declaration.  
The weird '[[..]]' notation stands for a list of walkable paths in our model of any arbitrary length.  
The `identity` and `sum` mentions are actually referencing our business logic, which expects 
as input the data found at the end of each of the listed paths. `sum` isn't an existing fn 
so lets create it:
```clojure
(defn sum [counts]
  (apply + counts))
```
Good. `sum` here is the analogue to your real pure business logic, which may amount to any 
number of fns and very elaborate. Everything around it, is how you chose to model your data, 
and how your data entities refer to one another.  
Next, we're missing some events to bootstrap our data:
```clojure
(def events ["foo's departments include rnd"
             "rnd's teams include engineers"
             "engineers' employees include joe"])
```
In other words, we have a joe, working as an engineer in the rnd department of a company named 'foo'.
Lets kick inference over what we have so far, and check joe's allegiance:
```clojure
(require '[ginfer.core :as g])
(def state (g/infer blueprints events :dialect "en"))
(finalize state)
(get-data state "employee/joe" :employee/allegiance)
;=> "department/rnd"
```
Yup. Just what we expected. Joe is part of the engineers team, which resides under the rnd department; 
so their allegiance is inferred to be the rnd department.<br>
Note that the order of events above does not matter - the outcome is always consistent, as expected.<br>
Now, what if we wanted to make some organizational changes?
```clojure
(def more-events ["zoo's company is foo"
                  "engineers' department is zoo"])
(def connectors (get-in state [:persistence :connectors]))
(def state (g/infer blueprints more-events :connectors connectors :dialect "en"))
(finalize state)
(get-data state "employee/joe" :employee/allegiance)
;=> "department/zoo"
(get-data state "company/foo" :company/head-count)
;=> 1
```
And Joe's allegiance is now to the Zoo department (and the company head-count remains 1).<br>
Again, the order of events doesn't matter. In fact, it doesn't even matter if "zoo's company is 
foo" or if "foo's departments include zoo", those are totally equivalent events as well.
Also, note our use of 'connectors'. We didn't explicitly pass in any before, so in-memory default 
connectors were created for us. We just made sure to pass these explicitly the second time around, 
to gain access into their data (in real-life, this data resides in your external db of course).

This example is quite powerful, because it is simple enough to follow, and yet - a product 
built on a similar premise in real-life can be quite elaborate and difficult to maintain. With 
any additional requirement, you'd be forced to consider an ever-growing, exponential list of use-
cases: what changes when teams shift departments? what gets impacted when employees move around? 
and so on. But using such a descriptive approach as demonstrated here, all possible use-cases are 
automatically taken care of, as guaranteed by the basic premise of this approach. Taken to extreme, 
you do not need a team of engineers, qa, and months of their time to add to development; a single 
product blueprints writer may be sufficient to handle or spearhead many use-cases, single-handed.

Other cool flavours of executions can be utilized as a drop-in replacement to `g/infer`.  
- `g/async-infer` returns a channel populated with the final state of inference once ready; execution 
is truly async - when any side-effects are async under the hood, parking semantics are in effect.
```clojure
;drop-in replacement for `g/infer` would be
(require '[clojure.core.async :refer [<!!]])
(comp <!! g/async-infer)
```
- `g/lazy-infer` returns a lazy sequence of execution steps along with the state at the time of 
execution, which takes effect as steps are taken from the sequence.
```clojure
;drop-in replacement for `g/infer` would be
(comp :state last g/lazy-infer)
```
- `g/lazy-async-infer` returns a channel populated with execution steps, as with `g/lazy-infer`. 
How many execution steps are eagerly calculated ahead of pulling them from the channel is 
configurable (default:16).
```clojure
;drop-in replacement for `g/infer` would be
(require '[seamless-async.core :refer [as-seq]])
(comp :state last as-seq g/lazy-async-infer)
```

As is usually the case with me, I extend the following list of tests which I use both to verify, 
and to explain my code:
- See basic [core](test/ginfer/t_core.clj) mechanics
- [BFF](test/ginfer/t_bff.clj): inference via reference
- [Vicious cycle](test/ginfer/t_vicious_cycle.clj): a cyclic case
- [Pets life](test/ginfer/t_pets_life.clj): order-agnostic consistency
- [Riddle](test/ginfer/t_riddle.clj): an elaborate, self-bootstrapping/self-solving riddle
- [Zero mem](test/ginfer/t_zero_mem.clj): use fs-connector in place of the default in-mem
- While not out-of-the-box feature, [this](test/ginfer/t_debugger.clj) hints of how debugging may be achieved
- [DR](test/ginfer/t_dr.clj) demonstration (or a pause/resume use-case, if you'd like)
- A demonstration of [pseudo-english](test/ginfer/t_en.clj) input
- Several use-cases involving [endpoints](test/ginfer/t_endpoints.clj) attributes
- Similarly, several [persistence](test/ginfer/t_persistence.clj) use-cases
- And a demonstration of the different [flavours](test/ginfer/t_flavours.clj) of execution

## License

Copyright © 2023 [@s-doti](https://github.com/s-doti)

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
