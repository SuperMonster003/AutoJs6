from pathlib import Path
import tempfile
import unittest

import arity
from arity import KotlinGuards, compare_ranges, cross_check, guard_range, overload_range, span


def param(name, kind='RAW', optional=False, vararg=False):
    return {'name': name, 'type': 'kotlin.Any?', 'kind': kind, 'optional': optional, 'vararg': vararg}


def ksp(*ranges, basis='params'):
    return {'source': 'ksp', 'overloads': [span(lo, hi, basis) for lo, hi in ranges]}


def dts(*ranges):
    return [{'min': lo, 'max': hi} for lo, hi in ranges]


class OverloadRangeTests(unittest.TestCase):
    def test_declared_vararg_and_parameter_lists(self):
        self.assertEqual(overload_range({'params': [param('args', vararg=True)], 'arity': {'min': 1, 'max': None}}), span(1, None, 'declared'))
        self.assertEqual(overload_range({'params': [param('args', vararg=True)], 'arity': None}), span(0, None, 'vararg'))
        self.assertEqual(overload_range({'params': [param('path', 'STRING'), param('mode', optional=True)], 'arity': None}), span(1, 2, 'params'))
        self.assertEqual(overload_range({'params': [param('base', 'STRING'), param('paths', 'STRING', vararg=True)], 'arity': None}), span(1, None, 'params'))
        # A typed single vararg converts every argument; only a raw one checks its arguments itself.
        self.assertEqual(overload_range({'params': [param('s', 'STRING', vararg=True)], 'arity': None}), span(0, None, 'params'))

    def test_guard_ranges(self):
        self.assertEqual(guard_range('OnlyOne', None, {}), (1, 1))
        self.assertEqual(guard_range('IsEmpty', None, {}), (0, 0))
        self.assertEqual(guard_range('NotEmpty', None, {}), (1, None))
        self.assertEqual(guard_range('Length', '2', {}), (2, 2))
        self.assertEqual(guard_range('AtLeast', '1', {}), (1, None))
        self.assertEqual(guard_range('AtMost', 'MAX', {'MAX': 3}), (0, 3))
        self.assertEqual(guard_range('LengthInRange', '1..4', {}), (1, 4))
        self.assertEqual(guard_range('LengthInRange', '1 until 4', {}), (1, 3))
        self.assertIsNone(guard_range('Length', 'arity', {}))


class KotlinGuardTests(unittest.TestCase):
    SOURCE = '''
object Sample {
    fun plain(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..3) { a ->
        a.size
    }

    // fun commented(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) { 0 }
    fun block(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        checkRuntime(runtime)
        check(runtime === other) { "different runtime" }
        return ensureArgumentsOnlyOne(args) { it }
    }

    fun wrapped(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsAtMost(args, LIMIT) { a -> a }
    }

    fun delegating(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = plain(scriptRuntime, args)

    fun variadic(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = unwrapArguments(args) { it.size }

    fun nested(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = when (args.size) {
        0 -> ensureArgumentsIsEmpty(args) { 0 }
        else -> 1
    }

    fun otherArray(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsLength(other, 2) { 0 }

    const val LIMIT = 2
}
'''

    def test_guards_wrappers_delegation_and_unknown_bodies(self):
        with tempfile.TemporaryDirectory() as directory:
            host = Path(directory)
            (host / 'Sample.kt').write_text(self.SOURCE, encoding='utf-8')
            guards = KotlinGuards(host)
            arity_of = lambda name: guards.arity('Sample.kt', name)
            self.assertEqual(arity_of('plain')['overloads'], [span(1, 3, 'ensureArguments', guard='LengthInRange')])
            self.assertEqual(arity_of('block')['overloads'], [span(1, 1, 'ensureArguments', guard='OnlyOne')])
            self.assertEqual(arity_of('wrapped')['overloads'], [span(0, 2, 'ensureArguments', guard='AtMost')])
            self.assertEqual(arity_of('delegating')['overloads'], [span(1, 3, 'ensureArguments', guard='LengthInRange')])
            self.assertEqual(arity_of('variadic')['overloads'], [span(0, None, 'vararg', guard='unwrapArguments')])
            self.assertEqual(arity_of('commented')['source'], 'unknown')
            self.assertEqual(arity_of('nested')['source'], 'unknown')
            self.assertEqual(arity_of('otherArray')['source'], 'unknown')


class CompareTests(unittest.TestCase):
    def test_disjoint_overloads_fail_and_wider_overloads_warn(self):
        self.assertEqual(compare_ranges(ksp((2, 2)), dts((3, 3), (2, 2))), ('fail', {'disjoint': ['3-3']}))
        self.assertEqual(compare_ranges(ksp((2, 3)), dts((1, 2), (3, 3))), ('wider', {'rejected': ['1']}))
        self.assertEqual(compare_ranges(ksp((1, 1), (4, 4)), dts((1, 4))), ('wider', {'rejected': ['2', '3']}))
        self.assertEqual(compare_ranges(ksp((1, None), basis='declared'), dts((0, None))), ('wider', {'rejected': ['0']}))
        self.assertEqual(compare_ranges(ksp((0, 2)), dts((1, None))), ('wider', {'rejected': ['3+']}))

    def test_narrower_compares_only_the_ends_of_spans(self):
        self.assertEqual(compare_ranges(ksp((0, 1)), dts((1, 1))), ('narrower', {'undeclared': ['0']}))
        self.assertEqual(compare_ranges(ksp((1, 3)), dts((1, 1))), ('narrower', {'undeclared': ['2-3']}))
        self.assertEqual(compare_ranges(ksp((1, None), basis='declared'), dts((1, 4))), ('narrower', {'undeclared': ['5+']}))
        # images.clip: optional parameters cover 3 and 4, which the body rejects; the ends match.
        self.assertEqual(compare_ranges(ksp((2, 5)), dts((5, 5), (2, 2))), ('match', {}))
        # An unbounded end from an undeclared vararg parameter (cvt.bytes checks 1-4 itself).
        self.assertEqual(compare_ranges(ksp((1, None)), dts((1, 2), (3, 4))), ('match', {}))

    def test_unchecked_unknown_and_missing_declarations(self):
        self.assertEqual(compare_ranges(ksp((0, None), basis='vararg'), dts((2, 2))), ('vararg', {}))
        self.assertEqual(compare_ranges({'source': 'unknown', 'overloads': []}, dts((1, 1))), ('unknown', {}))
        self.assertEqual(compare_ranges(ksp((1, 2)), None), ('no-dts', {}))
        self.assertEqual(compare_ranges(ksp((1, 2)), dts((1, 2))), ('match', {}))

    def test_rows_join_surfaces_by_api_and_callable_modules(self):
        baseline = {
            'records': [
                {'api': 'm.f', 'ownerModule': 'm', 'metadata': 'ksp', 'kind': 'Functions', 'arity': ksp((1, 2))},
                {'api': 'm.g', 'ownerModule': 'm', 'metadata': 'ksp', 'kind': 'Functions', 'arity': ksp((1, 1))},
                {'api': 'm.p', 'ownerModule': 'm', 'metadata': 'ksp', 'kind': 'Getters'},
            ],
            'modules': [{'module': 'm', 'metadata': 'ksp', 'invokeArity': ksp((0, 1)), 'constructArity': ksp((0, 1))}],
        }
        surfaces = [
            {'api': 'm.f', 'declaration': {'status': 'explicit', 'signatures': {'call': dts((1, 2)), 'construct': []}}},
            {'api': 'm.g', 'declaration': {'status': 'any'}},
            {'api': 'm', 'declaration': {'status': 'explicit', 'signatures': {'call': dts((0, 0)), 'construct': []}}},
        ]
        result = arity.compare(baseline, surfaces)
        self.assertEqual([(r['api'], r['status']) for r in result['rows']], [('m.()', 'narrower'), ('m.f', 'match'), ('m.g', 'no-dts'), ('m.new()', 'no-dts')])
        self.assertEqual(result['counts']['no-dts'], 2)


class CrossCheckTests(unittest.TestCase):
    def test_differences_and_undeclared_varargs_with_kotlin_guards(self):
        old = lambda lo, hi, guard='Length': {'source': 'ensureArguments', 'overloads': [span(lo, hi, 'ensureArguments', guard=guard)]}
        ksp_result = {'records': [
            {'api': 'a', 'ownerModule': 'm', 'arity': ksp((1, 2))},
            {'api': 'b', 'ownerModule': 'm', 'arity': ksp((1, 2))},
            {'api': 'c', 'ownerModule': 'm', 'arity': ksp((0, None), basis='vararg')},
            {'api': 'd', 'ownerModule': 'm', 'arity': ksp((0, None), basis='vararg')},
        ]}
        kotlin_result = {'records': [
            {'api': 'a', 'arity': old(1, 2, 'LengthInRange')},
            {'api': 'b', 'arity': old(2, 2)},
            {'api': 'c', 'arity': old(1, None, 'NotEmpty')},
            {'api': 'd', 'arity': {'source': 'ensureArguments', 'overloads': [span(0, None, 'vararg', guard='unwrapArguments')]}},
        ]}
        rows = cross_check(ksp_result, kotlin_result)
        self.assertEqual([(r['api'], r['kind'], r['ksp'], r['kotlin']) for r in rows], [('b', 'differs', '1-2', '2-2'), ('c', 'vararg-undeclared', '0-n', '1-n')])


if __name__ == '__main__':
    unittest.main()
